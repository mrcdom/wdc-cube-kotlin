package br.com.wdc.framework.rest.transaction

import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.exception.TransactionConflictException
import br.com.wdc.framework.domain.transaction.TransactionService
import br.com.wdc.framework.persistence.transaction.RemoteTransactionCoordinator
import br.com.wdc.framework.rest.RequestContext

/**
 * The bridge between the request and the transaction: what runs on a `transactional` route commits together, or
 * nothing commits.
 *
 * 1. [txHeader] came and there is a [coordinator]: the write joins the client's remote transaction and **doesn't
 *    commit** — the boundary is the client's. It runs as a participant ([service] `required`): if the request fails
 *    midway, the remote transaction is doomed, and the client's commit rolls it back (409) instead of committing the
 *    partial write.
 * 2. The header didn't come, but the owner has an open remote transaction: the header got lost on the way. The write
 *    is refused (409) instead of committing on its own, outside the transaction. Turn off with
 *    [refuseWritesOutsideOpenTransaction].
 * 3. Otherwise: [service] `required` — one transaction per request. Without a service, it runs directly.
 *
 * The header is optional: without an open remote transaction, the route is simply transactional. But **a server
 * without a [coordinator] refuses the header (400)**, in reads and writes: the client believes it is in a
 * transaction, and a write made outside it could not be undone.
 *
 * A read is not `transactional`, but it may join the client's remote transaction ([joinRemote]): with the header,
 * it sees what that transaction already wrote and is not blocked by the rows it locked.
 */
class RestTransactions {

    /** Where the local transactions come from, queried on each request. */
    var serviceSource: () -> TransactionService? = { null }

    /** Where the remote transaction coordinator comes from, queried on each request. */
    var coordinatorSource: () -> RemoteTransactionCoordinator? = { null }

    /** The local transactions. `null` = a `transactional` route runs without a transaction. */
    var service: TransactionService?
        get() = serviceSource()
        set(value) {
            serviceSource = { value }
        }

    /** The remote transactions, driven by the client. `null` = none. */
    var coordinator: RemoteTransactionCoordinator?
        get() = coordinatorSource()
        set(value) {
            coordinatorSource = { value }
        }

    var txHeader: String = "X-Tx-Id"

    /** Identifies the client instance when there is no authenticated user. */
    var clientHeader: String = "X-Client-Id"

    var refuseWritesOutsideOpenTransaction: Boolean = true

    /**
     * The opaque key of the remote transactions' owner, in separate namespaces so an anonymous caller can't pass
     * for a user: with a principal, `user:<id>/client:<clientHeader>` (or `user:<id>`, if the header didn't come);
     * without, `anon:<clientHeader>`, or `null` if the header didn't come.
     *
     * The client is part of the key so the same user on two clients (desktop and web) doesn't block itself: the
     * refusal of writes without [txHeader] and the transaction are per instance. The user stays in the key, and one
     * user's transaction remains unreachable by another.
     */
    var ownerKey: (RequestContext) -> String? = { request ->
        val client = request.header(clientHeader)?.takeIf { it.isNotBlank() }
        request.principal?.let { principal -> "user:${principal.id}" + (client?.let { "/client:$it" } ?: "") }
            ?: client?.let { "anon:$it" }
    }

    /**
     * The key of the cap of open transactions per owner: with a principal, `user:<id>` — the cap is the user's, and
     * changing [clientHeader] doesn't get around it —; without, the same as [ownerKey].
     */
    var quotaKey: (RequestContext) -> String? = { request ->
        request.principal?.let { "user:${it.id}" }
            ?: request.header(clientHeader)?.takeIf { it.isNotBlank() }?.let { "anon:$it" }
    }

    /**
     * Runs a read in the client's remote transaction, if the request carries [txHeader] and there is a [coordinator]:
     * as a participant, so it sees the transaction's own writes. Otherwise it runs directly, with no transaction (it
     * sees only what is committed). The transaction is released when the read ends.
     */
    suspend fun <T> joinRemote(request: RequestContext, block: suspend () -> T): T {
        val txId = remoteTxId(request) ?: return block()
        val coordinator = coordinator ?: return block() // remoteTxId already refused the header without one
        coordinator.resume(txId, ownerKey(request))
        try {
            val service = service ?: return block()
            return service.required { block() }
        } finally {
            coordinator.suspend(txId)
        }
    }

    /** Runs [block] in the transaction that fits the request (see the class). */
    suspend fun <T> run(request: RequestContext, block: suspend () -> T): T {
        val txId = remoteTxId(request)
        val coordinator = coordinator
        if (coordinator != null) {
            val owner = ownerKey(request)
            if (txId != null) {
                coordinator.resume(txId, owner)
                try {
                    val service = service ?: return block()
                    return service.required { block() }
                } finally {
                    coordinator.suspend(txId)
                }
            }
            if (refuseWritesOutsideOpenTransaction && owner != null && coordinator.hasOpenTransactionForOwner(owner)) {
                throw TransactionConflictException(
                    "Write without $txHeader while the caller has an open remote transaction — aborted to preserve atomicity"
                )
            }
        }
        val service = service ?: return block()
        return service.required { block() }
    }

    /** The [txHeader] of the request, or `null` if it didn't come. Refused (400) when there is no [coordinator]. */
    private fun remoteTxId(request: RequestContext): String? {
        val txId = request.header(txHeader)?.takeIf { it.isNotBlank() } ?: return null
        if (coordinator == null) {
            throw InvalidRequestException("$txHeader sent, but this server has no remote transactions")
        }
        return txId
    }
}
