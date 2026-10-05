package br.com.wdc.shopping.persistence.rest

import br.com.wdc.shopping.domain.ShoppingTransactions
import br.com.wdc.framework.domain.exception.TransactionConflictException
import io.javalin.http.Context
import kotlinx.coroutines.runBlocking

internal fun <T> blocking(block: suspend () -> T): T = runBlocking { block() }

/**
 * Executa uma escrita dentro da transação que lhe cabe:
 *
 * 1. Veio `X-Tx-Id` e há coordenador: a escrita se junta à transação remota do cliente (`resume` → handler →
 *    `suspend`, na thread da requisição) e **não confirma** — a fronteira é do cliente.
 * 2. Não veio `X-Tx-Id`, mas o solicitante tem transação remota aberta: o cabeçalho se perdeu no caminho. A
 *    escrita é recusada (409) em vez de confirmar sozinha, fora da transação, e deixar um registro órfão.
 * 3. Senão: transação isolada por requisição — tudo o que o handler gravar confirma junto, ou nada confirma.
 *    Sem `TransactionService` registrado, executa direto.
 *
 * A exceção do handler sobe com o tipo original, para os mapeamentos de erro continuarem valendo.
 */
internal fun <T> transactional(ctx: Context, block: suspend () -> T): T {
    val coordinator = RemoteTransactions.COORDINATOR.getOrNull()
    if (coordinator != null) {
        val owner = TxApiController.currentOwnerKey(ctx)
        val txId = ctx.header(TxApiController.TX_HEADER)
        if (!txId.isNullOrBlank()) {
            coordinator.resume(txId, owner)
            try {
                return runBlocking { block() }
            } finally {
                coordinator.suspend(txId)
            }
        }
        if (owner != null && coordinator.hasOpenTransactionForOwner(owner)) {
            throw TransactionConflictException(
                "Escrita sem ${TxApiController.TX_HEADER} enquanto há transação remota aberta para o solicitante — " +
                    "abortada para preservar a atomicidade"
            )
        }
    }
    return runBlocking {
        val transactions = ShoppingTransactions.BEAN.getOrNull()
        if (transactions == null) block() else transactions.required { block() }
    }
}
