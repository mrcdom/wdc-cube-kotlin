package br.com.wdc.store.domain

import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.domain.transaction.TransactionService

/**
 * Holder of the Store application's [TransactionService].
 *
 * Filled by the composition root: on the server, with the JDBC implementation bound to the module's DataSource;
 * on the client, with the one that demarcates the remote transaction over REST. It may be empty (tests without
 * persistence): the use case should then run directly — `StoreTransactions.BEAN.getOrNull()`.
 */
object StoreTransactions {
    val BEAN = AtomicRef<TransactionService>()
}
