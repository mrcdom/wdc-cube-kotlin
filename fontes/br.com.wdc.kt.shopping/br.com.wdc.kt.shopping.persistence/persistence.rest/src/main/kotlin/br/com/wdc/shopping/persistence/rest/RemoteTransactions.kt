package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.persistence.transaction.RemoteTransactionCoordinator

/**
 * Holder of the remote transaction coordinator — for the transactions the client opens and closes over REST.
 *
 * Populated by the composition root (backend, tests). When empty, the API offers no remote transaction: `/api/tx/…`
 * answers with an error and every write is a transaction of its own, per request.
 */
object RemoteTransactions {
    val COORDINATOR = AtomicRef<RemoteTransactionCoordinator>()
}
