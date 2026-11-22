package br.com.wdc.framework.persistence.transaction

/**
 * Coordinator of **client-driven transactions over REST** (server side).
 *
 * Lets the client demarcate a transaction that spans **several HTTP requests** against the same backend: the
 * server keeps the physical transaction open between calls, identified by a `txId`, and rebinds it to the
 * thread serving each request.
 *
 * - [begin] — opens a transaction, keeps it suspended and returns its `txId`;
 * - [resume] / [suspend] — bind/unbind the transaction on the current thread (around each request that
 *   carries the `txId`);
 * - [commit] / [rollback] — finish and remove from the registry.
 *
 * **Owner (`ownerKey`):** an **opaque** key given at [begin] and revalidated on every following operation —
 * the coordinator only compares strings, without knowing the concept of user. The calling layer translates
 * identity → key (and decides what to do with `null`).
 *
 * **Server-side persistence** SPI: consumed only by the REST host, hence outside the domain. Each `txId`
 * keeps a connection alive until commit/rollback — hence the cleanup of abandoned transactions.
 */
interface RemoteTransactionCoordinator {

    /**
     * Opens a (suspended) transaction and returns its opaque identifier.
     *
     * @param ownerKey opaque owner key (revalidated on following operations); `null` = no owner
     */
    fun begin(ownerKey: String?): String

    /**
     * Like [begin], with the per-owner open cap counted by [quotaKey] instead of [ownerKey]. Useful when the
     * owner is finer than whoever answers for the cap: owner = user + client instance, cap = user. Without
     * support in the implementation, falls back to [begin] (the cap stays per owner).
     */
    fun begin(ownerKey: String?, quotaKey: String?): String = begin(ownerKey)

    /** Rebinds the transaction to the current thread. Fails if unknown, already in use, or if [ownerKey] differs. */
    fun resume(txId: String, ownerKey: String?)

    /** Unbinds the transaction from the current thread, keeping it alive for the next request. */
    fun suspend(txId: String)

    /**
     * Finishes with COMMIT and removes from the registry (validates [ownerKey]). **Idempotent**: repeating the
     * commit of an already committed transaction (previous response lost) is a successful no-op, within the
     * outcome retention window. Repeating as rollback something already committed conflicts.
     */
    fun commit(txId: String, ownerKey: String?)

    /** Finishes with ROLLBACK and removes from the registry (validates [ownerKey]). **Idempotent**, symmetric to [commit]. */
    fun rollback(txId: String, ownerKey: String?)

    /**
     * State of the transaction for the caller, disambiguating a lost finish response.
     *
     * @return `"open"` (registered and alive), `"committed"`/`"rolledback"` (finished, within retention) or
     *         `"unknown"` (never existed or retention expired). Validates [ownerKey].
     */
    fun status(txId: String, ownerKey: String?): String

    /** `true` if there is a transaction registered for [txId]. */
    fun exists(txId: String?): Boolean

    /** Metrics snapshot (current gauges + accumulated counters). */
    fun stats(): RemoteTransactionStats

    /**
     * `true` if there is any remote transaction open for this (non-null) [ownerKey]. Lets the host detect a
     * write that arrived **without** the `txId` although the caller has an open remote transaction — a sign
     * of broken header propagation, which would otherwise autocommit outside the transaction.
     */
    fun hasOpenTransactionForOwner(ownerKey: String?): Boolean
}
