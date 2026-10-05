package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.util.AtomicRef
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
 */
class RestTransactionService(private val transport: HttpTransport) : TransactionService {

    private val current = AtomicRef<TxState>()

    init {
        // o transporte envia X-Tx-Id sempre que houver transação remota corrente
        transport.transactionIdSupplier = { current.getOrNull()?.txId }
    }

    override suspend fun <T> required(work: suspend (TransactionContext) -> T): T {
        val existing = current.getOrNull()
        return if (existing != null) work(existing) else runInNewTx(work)
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
        return work(state)
    }

    override suspend fun <T> supports(work: suspend (TransactionContext) -> T): T = work(current.getOrNull() ?: NoTx)

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

    private suspend fun <T> runInNewTx(work: suspend (TransactionContext) -> T): T {
        val state = TxState(remoteBegin())
        current.set(state)
        try {
            val result = work(state)
            // o txId ainda é o corrente: vai no cabeçalho do commit/rollback
            transport.postJson(if (state.rollbackOnly) "/api/tx/rollback" else "/api/tx/commit", "{}")
            return result
        } catch (e: Throwable) {
            // Melhor esforço: a falha do bloco é a que interessa. Se o commit falhou, o rollback do mesmo txId
            // é recusado ou inócuo no servidor.
            try {
                transport.postJson("/api/tx/rollback", "{}")
            } catch (_: Exception) {
            }
            throw e
        } finally {
            current.set(null)
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
