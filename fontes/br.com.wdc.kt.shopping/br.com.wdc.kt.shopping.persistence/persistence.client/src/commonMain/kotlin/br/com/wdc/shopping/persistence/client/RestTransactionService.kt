package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.domain.transaction.AfterCommitTasks
import br.com.wdc.framework.domain.transaction.TransactionContext
import br.com.wdc.framework.domain.transaction.TransactionNotAllowedException
import br.com.wdc.framework.domain.transaction.TransactionRequiredException
import br.com.wdc.framework.domain.transaction.TransactionService
import br.com.wdc.framework.domain.transaction.TransactionSystemException

/**
 * O [TransactionService] do cliente: demarca, por REST, uma transação que vive no servidor.
 *
 * `required` abre a transação remota (`POST /api/tx/begin` → `txId`); enquanto o bloco roda, o transporte
 * envia o `txId` em `X-Tx-Id` e as escritas se juntam à mesma transação física no servidor; ao fim, `commit`
 * (retorno normal, salvo `setRollbackOnly`) ou `rollback` (exceção). É o que torna atômicas várias escritas REST.
 *
 * **A transação corrente é uma por instância** — isto é, por aplicação cliente —, e não por thread: as ações
 * da apresentação rodam em série e o HTTP é bloqueante, de modo que não há duas correndo ao mesmo tempo. Daí
 * a regra para quem usa: **as chamadas de uma transação remota são serializadas**; um bloco que suspenda
 * esperando outra coisa que não o próprio HTTP abre espaço para outra ação herdar o `txId` (o servidor, de
 * todo modo, recusa o uso concorrente do mesmo `txId`).
 *
 * As leituras feitas dentro do bloco não participam da transação: enxergam só o que já foi confirmado.
 *
 * `notSupported` dentro de uma transação remota serve para **ler** fora dela, não para escrever: o servidor
 * não distingue "transação suspensa" de "cabeçalho perdido" e recusa (409) a escrita sem `X-Tx-Id` de quem tem
 * transação aberta. Para escrever por fora, use `requiresNew`.
 *
 * [afterCommit] guarda a tarefa na transação remota corrente e a roda depois que o commit remoto volta com sucesso,
 * já sem transação corrente; sem transação remota, roda na hora.
 */
class RestTransactionService(private val transport: HttpTransport) : TransactionService {

    private val current = AtomicRef<TxState>()

    init {
        // o transporte envia X-Tx-Id sempre que houver transação remota corrente
        transport.transactionIdSupplier = { current.getOrNull()?.txId }
    }

    override suspend fun <T> required(work: suspend (TransactionContext) -> T): T {
        val existing = current.getOrNull()
        return if (existing != null) participate(existing, work) else runInNewTx(work)
    }

    override suspend fun <T> requiresNew(work: suspend (TransactionContext) -> T): T {
        // suspende a externa: as chamadas do bloco não levam o txId dela
        val outer = current.getOrNull()
        current.set(null)
        try {
            return runInNewTx(work)
        } finally {
            current.set(outer)
        }
    }

    override suspend fun <T> mandatory(work: suspend (TransactionContext) -> T): T {
        val state = current.getOrNull() ?: throw TransactionRequiredException("mandatory: nenhuma transação remota ativa")
        return participate(state, work)
    }

    override suspend fun <T> supports(work: suspend (TransactionContext) -> T): T {
        val state = current.getOrNull() ?: return work(NoTx)
        return participate(state, work)
    }

    override suspend fun <T> notSupported(work: suspend (TransactionContext) -> T): T {
        val outer = current.getOrNull()
        current.set(null)
        try {
            return work(NoTx)
        } finally {
            current.set(outer)
        }
    }

    override suspend fun <T> never(work: suspend (TransactionContext) -> T): T {
        if (current.getOrNull() != null) {
            throw TransactionNotAllowedException("never: há transação remota ativa")
        }
        return work(NoTx)
    }

    override suspend fun afterCommit(task: suspend () -> Unit) {
        val state = current.getOrNull()
        if (state != null) state.afterCommit.add(task) else AfterCommitTasks.runAll(listOf(task))
    }

    /**
     * Executa [work] como participante da transação remota corrente. A falha condena a transação, mesmo que quem
     * chamou a capture e siga: é o que faz a transação local (`TransactionServiceImpl`), e sem isso o commit
     * confirmaria no servidor o que o participante gravou antes de falhar.
     */
    private suspend fun <T> participate(state: TxState, work: suspend (TransactionContext) -> T): T {
        try {
            return work(state)
        } catch (e: Throwable) {
            state.rollbackOnly = true
            throw e
        }
    }

    private suspend fun <T> runInNewTx(work: suspend (TransactionContext) -> T): T {
        val state = TxState(remoteBegin())
        current.set(state)
        val result: T
        try {
            result = try {
                work(state)
            } catch (e: Throwable) {
                // a falha do bloco é a que interessa
                rollbackQuietly()
                throw e
            }
            // o txId ainda é o corrente: vai no cabeçalho do commit/rollback
            if (state.rollbackOnly) transport.postJson("/api/tx/rollback", "{}") else commit()
        } finally {
            current.set(null)
        }
        // o commit remoto voltou com sucesso, e já não há transação corrente: uma tarefa que precise abre a sua
        val tasks = state.afterCommit.drain()
        if (!state.rollbackOnly) AfterCommitTasks.runAll(tasks)
        return result
    }

    /**
     * Pede o commit. Resposta do servidor (sucesso ou recusa) é definitiva. Falha de transporte não diz o que
     * aconteceu — o commit pode ter chegado e a resposta se perdido —: o cliente pergunta o desfecho
     * (`GET /api/tx/status`) antes de decidir. Comitou: sucesso. Ainda aberta: desfaz e falha. Desfeita, desconhecida
     * ou sem resposta: falha.
     */
    private fun commit() {
        try {
            transport.postJson("/api/tx/commit", "{}")
        } catch (e: Throwable) {
            if (e.isHttpAnswer()) {
                rollbackQuietly() // recusado: o rollback do mesmo txId é aceito ou inócuo
                throw e
            }
            when (remoteStatus()) {
                "committed" -> return
                "open" -> rollbackQuietly()
            }
            throw e
        }
    }

    /** O desfecho da transação corrente no servidor, ou `null` se nem isso se pôde saber. */
    private fun remoteStatus(): String? = try {
        // getBytes é autenticado e leva o X-Tx-Id em todos os transportes
        val text = transport.getBytes("/api/tx/status")?.decodeToString() ?: return null
        val input = JsonInputFactory.createStringInput(text).input
        var status: String? = null
        input.beginObject()
        while (input.hasNext()) {
            if (input.nextName() == "status") status = input.nextString() else input.skipValue()
        }
        input.endObject()
        status
    } catch (_: Exception) {
        null
    }

    private fun rollbackQuietly() {
        try {
            transport.postJson("/api/tx/rollback", "{}")
        } catch (_: Exception) {
            // melhor esforço: recusado (já finalizada) ou sem rede; a transação abandonada é revertida pelo servidor
        }
    }

    private fun remoteBegin(): String {
        val input = JsonInputFactory.createStringInput(transport.postJson("/api/tx/begin", "{}")).input
        var txId: String? = null
        input.beginObject()
        while (input.hasNext()) {
            if (input.nextName() == "txId") txId = input.nextString() else input.skipValue()
        }
        input.endObject()
        if (txId.isNullOrBlank()) {
            throw TransactionSystemException("Resposta de begin sem txId")
        }
        return txId
    }

    /** Uma transação remota ativa. */
    private class TxState(val txId: String) : TransactionContext {
        var rollbackOnly = false
        val afterCommit = AfterCommitTasks()

        override fun setRollbackOnly() {
            rollbackOnly = true
        }

        override fun isRollbackOnly(): Boolean = rollbackOnly

        override fun isActive(): Boolean = true
    }

    /** O contexto entregue quando não há transação (`supports`, `notSupported`, `never`). */
    private object NoTx : TransactionContext {
        override fun setRollbackOnly(): Unit =
            throw IllegalStateException("setRollbackOnly: nenhuma transação remota ativa neste escopo")

        override fun isRollbackOnly(): Boolean = false

        override fun isActive(): Boolean = false
    }
}
