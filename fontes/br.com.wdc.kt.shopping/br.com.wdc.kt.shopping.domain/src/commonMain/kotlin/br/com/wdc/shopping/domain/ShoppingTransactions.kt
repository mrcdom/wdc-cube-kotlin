package br.com.wdc.shopping.domain

import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.domain.transaction.TransactionService

/**
 * Holder do [TransactionService] da aplicação Shopping.
 *
 * Populado pelo composition root: no servidor, com a implementação JDBC ligada ao DataSource do módulo; no
 * cliente, com a que demarca a transação remota via REST. Pode estar vazio (testes sem persistência): o
 * caso de uso deve executar direto nesse caso — `ShoppingTransactions.BEAN.getOrNull()`.
 */
object ShoppingTransactions {
    val BEAN = AtomicRef<TransactionService>()
}
