package br.com.wdc.framework.persistence.transaction

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.domain.exception.TransactionConflictException
import br.com.wdc.framework.domain.exception.TransactionLimitExceededException
import br.com.wdc.framework.domain.transaction.TransactionSystemException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource

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

    /** Desfecho de transações finalizadas, retido por `outcomeRetentionMs` para idempotência. */
    private val outcomes = ConcurrentHashMap<String, Finalized>()

    private val begunCount = AtomicLong()
    private val committedCount = AtomicLong()
    private val rolledBackCount = AtomicLong()
    private val reapedCount = AtomicLong()
    private val rejectedByLimitCount = AtomicLong()

    override fun begin(ownerKey: String?): String {
        reapExpired() // recupera transações expiradas antes de avaliar os tetos
        enforceLimits(ownerKey)
        val ds = dataSource() ?: throw TransactionSystemException("DataSource não inicializado para o coordenador remoto")
        val scope = try {
            TransactionScope.open(ds) // owner, sem dono de thread: já nasce suspenso
        } catch (e: Exception) {
            throw TransactionSystemException("Falha ao abrir transação remota", e)
        }
        val txId = UUID.randomUUID().toString()
        registry[txId] = Entry(scope, ownerKey, clock())
        trackOwner(ownerKey)
        begunCount.incrementAndGet()
        LOG.debug("tx remota aberta: {} (owner={}, abertas={})", txId, ownerKey, registry.size)
        return txId
    }

    override fun resume(txId: String, ownerKey: String?) {
        val entry = registry[txId] ?: throw unknown(txId)
        verifyOwner(entry.owner, ownerKey)
        if (!entry.inUse.compareAndSet(false, true)) {
            throw TransactionSystemException("Transação remota em uso concorrente: $txId")
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
        val entry = registry[txId] ?: return
        try {
            TransactionScope.suspend()
        } finally {
            entry.lastUsedAt = clock()
            entry.inUse.set(false)
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
        if (ownerKey != null && (openByOwner[ownerKey] ?: 0) >= options.maxOpenPerOwner) {
            rejectedByLimitCount.incrementAndGet()
            LOG.warn("teto POR DONO de transações remotas atingido ({}) — owner={}", options.maxOpenPerOwner, ownerKey)
            throw TransactionLimitExceededException(
                "Limite de transações remotas abertas por dono atingido (${options.maxOpenPerOwner})"
            )
        }
    }

    private fun trackOwner(owner: String?) {
        if (owner != null) {
            openByOwner.merge(owner, 1, Int::plus)
        }
    }

    private fun untrackOwner(owner: String?) {
        if (owner != null) {
            openByOwner.computeIfPresent(owner) { _, count -> if (count <= 1) null else count - 1 }
        }
    }

    private fun finish(txId: String, ownerKey: String?, rollback: Boolean) {
        val entry = registry[txId]
        if (entry != null) {
            verifyOwner(entry.owner, ownerKey)
            if (registry.remove(txId, entry)) {
                untrackOwner(entry.owner)
                closeAndRecord(txId, entry, rollback)
                return
            }
            // outra thread finalizou entre o get e o remove → trata como repetição
        }
        finishAlreadyFinalized(txId, ownerKey, rollback)
    }

    /** Fecha o escopo (commit/rollback) e grava o desfecho para idempotência. */
    private fun closeAndRecord(txId: String, entry: Entry, rollback: Boolean) {
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
                if ((idleExpired || lifetimeExpired) && !entry.inUse.get() && registry.remove(txId, entry)) {
                    untrackOwner(entry.owner)
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

    private fun unknown(txId: String) = TransactionSystemException("Transação remota desconhecida ou expirada: $txId")

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
        val createdAt: Long,
    ) {
        val inUse = AtomicBoolean(false)

        @Volatile
        var lastUsedAt: Long = createdAt
    }

    /** Desfecho retido de uma transação finalizada. */
    private class Finalized(val committed: Boolean, val owner: String?, val at: Long)

    private companion object {
        val LOG = Log.getLogger("RemoteTransactionCoordinatorImpl")
    }
}
