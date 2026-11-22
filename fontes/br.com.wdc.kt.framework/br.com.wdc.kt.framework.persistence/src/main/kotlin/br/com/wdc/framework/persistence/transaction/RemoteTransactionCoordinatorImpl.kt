package br.com.wdc.framework.persistence.transaction

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.domain.exception.TransactionConflictException
import br.com.wdc.framework.domain.exception.TransactionLimitExceededException
import br.com.wdc.framework.domain.transaction.AfterCommitTasks
import br.com.wdc.framework.domain.transaction.RemoteTransactionNotFoundException
import br.com.wdc.framework.domain.transaction.TransactionSystemException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import kotlinx.coroutines.runBlocking

/**
 * Coordenador de transação remota (lado servidor), apoiado no [TransactionScope].
 *
 * Cada `txId` guarda um [TransactionScope] **suspenso** (transação física viva, sem dono de thread). A cada
 * requisição que carrega o `txId`, [resume] religa o escopo à thread corrente e [suspend] o desliga ao
 * final, mantendo-o vivo para a próxima. [commit]/[rollback] finalizam e removem.
 *
 * Transações ociosas além do timeout, ou vivas além do tempo máximo, são revertidas e removidas (varredura
 * preguiçosa no [begin]), evitando vazamento de conexão por clientes que abandonam a transação.
 *
 * **Threads.** [resume] e [suspend] agem na thread que os chama: o host deve executar `resume` → trabalho →
 * `suspend` na mesma thread (é o que acontece quando o handler roda com `runBlocking` na thread do request).
 *
 * As tarefas de `afterCommit` agendadas dentro de uma transação remota rodam no [commit] dela, depois de devolvida a
 * conexão, na thread de quem pediu o commit; desfeita (ou revertida por abandono), são descartadas.
 *
 * @param dataSource DataSource do módulo, resolvido a cada abertura
 * @param clock      relógio em milissegundos (injetável para teste)
 */
class RemoteTransactionCoordinatorImpl(
    private val dataSource: () -> DataSource?,
    private val options: RemoteTransactionOptions = RemoteTransactionOptions.defaults(),
    private val clock: () -> Long = System::currentTimeMillis,
) : RemoteTransactionCoordinator {

    private val registry = ConcurrentHashMap<String, Entry>()

    /** Índice dono → nº de transações abertas (donos nulos não entram). */
    private val openByOwner = ConcurrentHashMap<String, Int>()

    /** Índice da chave do teto → nº de transações abertas (o teto por dono é contado aqui). */
    private val openByQuota = ConcurrentHashMap<String, Int>()

    /** Desfeitas enquanto uma requisição as usava: fecham quando ela as desliga ([suspend]). */
    private val closingInUse = ConcurrentHashMap<String, Entry>()

    /** Desfecho de transações finalizadas, retido por `outcomeRetentionMs` para idempotência. */
    private val outcomes = ConcurrentHashMap<String, Finalized>()

    private val begunCount = AtomicLong()
    private val committedCount = AtomicLong()
    private val rolledBackCount = AtomicLong()
    private val reapedCount = AtomicLong()
    private val rejectedByLimitCount = AtomicLong()

    override fun begin(ownerKey: String?): String = begin(ownerKey, ownerKey)

    override fun begin(ownerKey: String?, quotaKey: String?): String {
        reapExpired() // recupera transações expiradas antes de avaliar os tetos
        enforceLimits(quotaKey)
        val ds = dataSource() ?: throw TransactionSystemException("DataSource não inicializado para o coordenador remoto")
        val scope = try {
            TransactionScope.open(ds) // owner, sem dono de thread: já nasce suspenso
        } catch (e: Exception) {
            throw TransactionSystemException("Falha ao abrir transação remota", e)
        }
        val txId = UUID.randomUUID().toString()
        registry[txId] = Entry(scope, ownerKey, quotaKey, clock())
        trackOwner(ownerKey, quotaKey)
        begunCount.incrementAndGet()
        LOG.debug("tx remota aberta: {} (owner={}, abertas={})", txId, ownerKey, registry.size)
        return txId
    }

    override fun resume(txId: String, ownerKey: String?) {
        val entry = registry[txId] ?: throw unknown(txId)
        verifyOwner(entry.owner, ownerKey)
        synchronized(entry) {
            if (entry.finished) throw unknown(txId)
            if (!entry.inUse.compareAndSet(false, true)) {
                throw TransactionConflictException("Transação remota em uso por outra requisição: $txId")
            }
        }
        try {
            TransactionScope.resume(entry.scope)
            entry.lastUsedAt = clock()
        } catch (e: RuntimeException) {
            entry.inUse.set(false)
            throw e
        }
    }

    override fun suspend(txId: String) {
        val entry = registry[txId] ?: closingInUse[txId]
        try {
            // sempre: mesmo sem entrada, a thread não pode ficar com o escopo ligado
            TransactionScope.suspend()
        } finally {
            if (entry != null) {
                entry.lastUsedAt = clock()
                val closeNow = synchronized(entry) {
                    entry.inUse.set(false)
                    entry.finished && closingInUse.remove(txId, entry)
                }
                // desfeita enquanto esta requisição a usava: fecha agora, já desligada da thread
                if (closeNow) rollbackQuietly(entry)
            }
        }
    }

    override fun commit(txId: String, ownerKey: String?) = finish(txId, ownerKey, rollback = false)

    override fun rollback(txId: String, ownerKey: String?) = finish(txId, ownerKey, rollback = true)

    override fun status(txId: String, ownerKey: String?): String {
        registry[txId]?.let { entry ->
            verifyOwner(entry.owner, ownerKey)
            return "open"
        }
        outcomes[txId]?.let { prior ->
            verifyOwner(prior.owner, ownerKey)
            return if (prior.committed) "committed" else "rolledback"
        }
        return "unknown"
    }

    override fun exists(txId: String?): Boolean = txId != null && registry.containsKey(txId)

    override fun hasOpenTransactionForOwner(ownerKey: String?): Boolean =
        ownerKey != null && openByOwner.containsKey(ownerKey)

    override fun stats(): RemoteTransactionStats = RemoteTransactionStats(
        openNow = registry.size,
        ownersWithOpen = openByOwner.size,
        retainedOutcomes = outcomes.size,
        begun = begunCount.get(),
        committed = committedCount.get(),
        rolledBack = rolledBackCount.get(),
        reaped = reapedCount.get(),
        rejectedByLimit = rejectedByLimitCount.get(),
    )

    // -------------------------------------------------------------------------

    /**
     * Rejeita a abertura se um teto for atingido. Limite **frouxo**: sob alta concorrência pode haver leve
     * ultrapassagem (verificação não-atômica), aceitável — o objetivo é proteger o pool, não contar exato.
     */
    private fun enforceLimits(ownerKey: String?) {
        if (registry.size >= options.maxOpen) {
            rejectedByLimitCount.incrementAndGet()
            LOG.warn("teto GLOBAL de transações remotas atingido ({}) — abertura rejeitada", options.maxOpen)
            throw TransactionLimitExceededException(
                "Limite global de transações remotas abertas atingido (${options.maxOpen})"
            )
        }
        if (ownerKey != null && (openByQuota[ownerKey] ?: 0) >= options.maxOpenPerOwner) {
            rejectedByLimitCount.incrementAndGet()
            LOG.warn("teto POR DONO de transações remotas atingido ({}) — owner={}", options.maxOpenPerOwner, ownerKey)
            throw TransactionLimitExceededException(
                "Limite de transações remotas abertas por dono atingido (${options.maxOpenPerOwner})"
            )
        }
    }

    private fun trackOwner(owner: String?, quota: String?) {
        if (owner != null) openByOwner.merge(owner, 1, Int::plus)
        if (quota != null) openByQuota.merge(quota, 1, Int::plus)
    }

    private fun untrackOwner(entry: Entry) {
        entry.owner?.let { owner -> openByOwner.computeIfPresent(owner) { _, count -> if (count <= 1) null else count - 1 } }
        entry.quota?.let { quota -> openByQuota.computeIfPresent(quota) { _, count -> if (count <= 1) null else count - 1 } }
    }

    private fun finish(txId: String, ownerKey: String?, rollback: Boolean) {
        val entry = registry[txId]
        if (entry != null) {
            verifyOwner(entry.owner, ownerKey)
            val action = synchronized(entry) {
                when {
                    entry.finished -> Finish.ALREADY
                    entry.inUse.get() -> {
                        // Uma requisição usa a transação agora (o cliente desistiu dela por timeout, por exemplo).
                        // Fechar a conexão debaixo do statement em curso a faria falhar de um jeito qualquer: o
                        // commit é recusado, e o rollback vale já como desfecho, mas fecha quando ela desligar.
                        if (!rollback) {
                            throw TransactionConflictException("Transação remota em uso por uma requisição: $txId; tente de novo")
                        }
                        entry.finished = true
                        registry.remove(txId, entry)
                        closingInUse[txId] = entry
                        Finish.DEFERRED
                    }
                    else -> {
                        entry.finished = true
                        registry.remove(txId, entry)
                        Finish.NOW
                    }
                }
            }
            when (action) {
                Finish.NOW -> {
                    untrackOwner(entry)
                    closeAndRecord(txId, entry, rollback)
                    return
                }
                Finish.DEFERRED -> {
                    untrackOwner(entry)
                    outcomes[txId] = Finalized(false, entry.owner, clock())
                    rolledBackCount.incrementAndGet()
                    LOG.debug("tx remota desfeita em uso; fecha quando a requisição desligar: {}", txId)
                    return
                }
                Finish.ALREADY -> Unit // outra thread finalizou: trata como repetição
            }
        }
        finishAlreadyFinalized(txId, ownerKey, rollback)
    }

    /** Fecha o escopo (commit/rollback) e grava o desfecho para idempotência. */
    private fun closeAndRecord(txId: String, entry: Entry, rollback: Boolean) {
        // Commit pedido, mas uma requisição falhou dentro da transação (ou alguém marcou rollback): desfaz e diz,
        // em vez de responder sucesso a quem acha que confirmou.
        val condemned = !rollback && entry.scope.isRollbackOnly()
        if (condemned) {
            closeAndRecord(txId, entry, rollback = true)
            throw TransactionConflictException(
                "Transação $txId desfeita: uma requisição dentro dela falhou (ou ela foi marcada para rollback)"
            )
        }
        try {
            if (rollback) {
                entry.scope.setRollbackOnly()
            }
            // fecha sem religar à thread: em JDBC o commit não depende de afinidade (com JTA, religar antes)
            entry.scope.close() // owner: commit (ou rollback se marcado) e fecha a conexão
        } catch (e: Exception) {
            throw TransactionSystemException("Falha ao finalizar transação remota $txId", e)
        }
        outcomes[txId] = Finalized(!rollback, entry.owner, clock())
        (if (rollback) rolledBackCount else committedCount).incrementAndGet()
        LOG.debug("tx remota finalizada ({}): {} (abertas={})", if (rollback) "rollback" else "commit", txId, registry.size)
        runAfterCommit(entry.scope)
    }

    /**
     * As tarefas de `afterCommit` agendadas dentro da transação remota, se ela comitou. Rodam aqui, já com a
     * conexão devolvida, e sem transação ligada à thread: uma tarefa que precise de uma abre a sua.
     */
    private fun runAfterCommit(scope: TransactionScope) {
        val tasks = scope.takeAfterCommit()
        if (tasks.isEmpty()) return
        val bound = TransactionScope.suspend()
        try {
            runBlocking { AfterCommitTasks.runAll(tasks) }
        } finally {
            TransactionScope.resume(bound)
        }
    }

    /** Repetição: a transação não está mais aberta. Idempotente se o desfecho anterior bate com o pedido. */
    private fun finishAlreadyFinalized(txId: String, ownerKey: String?, rollback: Boolean) {
        val prior = outcomes[txId] ?: throw unknown(txId)
        verifyOwner(prior.owner, ownerKey)
        if (prior.committed == rollback) {
            throw TransactionConflictException(
                "Transação $txId já finalizada como ${if (prior.committed) "committed" else "rolledback"}; " +
                    "não aceita o desfecho oposto"
            )
        }
        // mesmo desfecho já aplicado → no-op de sucesso (resposta anterior perdida)
    }

    private fun reapExpired() {
        val now = clock()
        if (registry.isNotEmpty()) {
            val idleDeadline = now - options.idleTimeoutMs
            for ((txId, entry) in registry) {
                val idleExpired = entry.lastUsedAt < idleDeadline
                val lifetimeExpired = (now - entry.createdAt) >= options.maxLifetimeMs
                val reap = (idleExpired || lifetimeExpired) && synchronized(entry) {
                    if (entry.finished || entry.inUse.get()) {
                        false
                    } else {
                        entry.finished = true
                        registry.remove(txId, entry)
                    }
                }
                if (reap) {
                    untrackOwner(entry)
                    rollbackQuietly(entry)
                    outcomes[txId] = Finalized(false, entry.owner, now) // reaped = rolledback (desambigua a repetição)
                    reapedCount.incrementAndGet()
                    LOG.info(
                        "tx remota abandonada revertida ({}): {} owner={}",
                        if (lifetimeExpired) "lifetime" else "idle", txId, entry.owner,
                    )
                }
            }
        }
        if (outcomes.isNotEmpty()) {
            val outcomeDeadline = now - options.outcomeRetentionMs
            outcomes.entries.removeIf { it.value.at < outcomeDeadline }
        }
    }

    private fun rollbackQuietly(entry: Entry) {
        try {
            entry.scope.setRollbackOnly()
            entry.scope.close()
        } catch (_: Exception) {
            // melhor esforço — transação abandonada
        }
    }

    private fun unknown(txId: String) = RemoteTransactionNotFoundException("Transação remota desconhecida ou expirada: $txId")

    /** Se a transação tem dono (chave não-nula no begin), exige que a operação apresente a mesma chave. */
    private fun verifyOwner(owner: String?, requesterKey: String?) {
        if (owner != null && owner != requesterKey) {
            throw AccessDeniedException("Transação remota não pertence ao solicitante")
        }
    }

    private class Entry(
        val scope: TransactionScope,
        /** Chave opaca do dono. `null` = transação sem dono (segurança desativada). */
        val owner: String?,
        /** Chave do teto por dono (a mesma do dono, salvo [begin] com `quotaKey`). */
        val quota: String?,
        val createdAt: Long,
    ) {
        val inUse = AtomicBoolean(false)

        /** Finalizada (ou com rollback adiado): não aceita mais requisições. Mexa só com o lock da entrada. */
        var finished = false

        @Volatile
        var lastUsedAt: Long = createdAt
    }

    private enum class Finish { NOW, DEFERRED, ALREADY }

    /** Desfecho retido de uma transação finalizada. */
    private class Finalized(val committed: Boolean, val owner: String?, val at: Long)

    private companion object {
        val LOG = Log.getLogger("RemoteTransactionCoordinatorImpl")
    }
}
