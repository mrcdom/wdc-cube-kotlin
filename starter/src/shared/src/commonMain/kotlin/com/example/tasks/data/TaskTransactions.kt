package com.example.tasks.data

import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.domain.transaction.TransactionService

/**
 * The transaction service the write calls run in (`ResourceCreate`, `ResourceUpdate`, `ResourceDelete` open one around
 * their work). Empty here, and the writes run directly: on the frontend each call is one request, answered whole by the
 * backend; the starter's backend keeps the tasks in memory. A backend with a database registers its own, and each
 * write action then runs in a transaction.
 */
object TaskTransactions {
    val BEAN = AtomicRef<TransactionService>()
}
