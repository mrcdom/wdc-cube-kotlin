package br.com.wdc.shopping.persistence.rest

import br.com.wdc.shopping.domain.ShoppingTransactions
import kotlinx.coroutines.runBlocking

internal fun <T> blocking(block: suspend () -> T): T = runBlocking { block() }

/**
 * Executa uma escrita dentro de uma transação isolada por requisição: tudo o que o handler gravar confirma
 * junto, ou nada confirma. Sem `TransactionService` registrado, executa direto.
 *
 * A exceção do handler sobe com o tipo original, para os mapeamentos de erro continuarem valendo.
 */
internal fun <T> transactional(block: suspend () -> T): T = runBlocking {
    val transactions = ShoppingTransactions.BEAN.getOrNull()
    if (transactions == null) block() else transactions.required { block() }
}
