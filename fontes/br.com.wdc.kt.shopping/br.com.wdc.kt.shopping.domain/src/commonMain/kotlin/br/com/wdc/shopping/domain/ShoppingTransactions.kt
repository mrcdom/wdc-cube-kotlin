package br.com.wdc.shopping.domain

import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.domain.transaction.TransactionService

/**
 * Holder of the Shopping application's [TransactionService].
 *
 * Filled by the composition root: on the server, with the JDBC implementation bound to the module's DataSource;
 * on the client, with the one that demarcates the remote transaction over REST. It may be empty (tests without
 * persistence): the use case should then run directly — `ShoppingTransactions.BEAN.getOrNull()`.
 */
object ShoppingTransactions {
    val BEAN = AtomicRef<TransactionService>()
}
