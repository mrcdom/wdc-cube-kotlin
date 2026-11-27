package com.example.tasks.data

import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.domain.transaction.TransactionService

/**
 * The transaction service the write calls run in (`ResourceCreate`, `ResourceUpdate`, `ResourceDelete` open one around
 * their work). On the frontend it stays empty and the writes run directly: each call is one request, answered whole by
 * the backend. The backend registers its database's (`TaskDatabase.open`), so each write action runs in a transaction:
 * all its statements are committed together, or none of them when it throws.
 */
object TaskTransactions {
    val BEAN = AtomicRef<TransactionService>()
}
