package br.com.wdc.framework.persistence.transaction

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.domain.exception.TransactionConflictException
import br.com.wdc.framework.domain.exception.TransactionLimitExceededException
import br.com.wdc.framework.domain.transaction.AfterCommitTasks
import br.com.wdc.framework.domain.transaction.RemoteTransactionNotFoundException
import br.com.wdc.framework.domain.transaction.TransactionSystemException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import kotlinx.coroutines.runBlocking

/**
 * Remote transaction coordinator (server side), built on [TransactionScope].
 *
 * Each `txId` holds a **suspended** [TransactionScope] (live physical transaction, no thread owner). On each
 * request that carries the `txId`, [resume] rebinds the scope to the current thread and [suspend] unbinds it
 * at the end, keeping it alive for the next one. [commit]/[rollback] finish and remove.
 *
 * Transactions idle past the timeout, or alive past the maximum lifetime, are rolled back and removed (lazy
 * sweep in [begin]), avoiding connection leaks from clients that abandon the transaction.
 *
 * **Threads.** [resume] and [suspend] act on the calling thread: the host must run `resume` → work →
 * `suspend` on the same thread (which is what happens when the handler runs with `runBlocking` on the request thread).
 *
 * `afterCommit` tasks scheduled inside a remote transaction run on its [commit], after the connection is
 * returned, on the thread that requested the commit; if rolled back (or reaped as abandoned), they are discarded.
 *
 * @param dataSource the module's DataSource, resolved on each opening
 * @param clock      clock in milliseconds (injectable for tests)
 */
class RemoteTransactionCoordinatorImpl(
    private val dataSource: () -> DataSource?,
    private val options: RemoteTransactionOptions = RemoteTransactionOptions.defaults(),
    private val clock: () -> Long = System::currentTimeMillis,
) : RemoteTransactionCoordinator {

    private val registry = ConcurrentHashMap<String, Entry>()

    /** Index owner → number of open transactions (null owners are left out). */
    private val openByOwner = ConcurrentHashMap<String, Int>()

    /** Index cap key → number of open transactions (the per-owner cap is counted here). */
    private val openByQuota = ConcurrentHashMap<String, Int>()

    /** Rolled back while a request was using them: they close when it unbinds them ([suspend]). */
    private val closingInUse = ConcurrentHashMap<String, Entry>()

    /** Outcome of finished transactions, retained for `outcomeRetentionMs` for idempotency. */
    private val outcomes = ConcurrentHashMap<String, Finalized>()

    private val begunCount = AtomicLong()
    private val committedCount = AtomicLong()
    private val rolledBackCount = AtomicLong()
    private val reapedCount = AtomicLong()
    private val rejectedByLimitCount = AtomicLong()

    override fun begin(ownerKey: String?): String = begin(ownerKey, ownerKey)

    override fun begin(ownerKey: String?, quotaKey: String?): String {
        reapExpired() // reclaims expired transactions before checking the caps
        enforceLimits(quotaKey)
        val ds = dataSource() ?: throw TransactionSystemException("DataSource not initialized for the remote coordinator")
        val scope = try {
            TransactionScope.open(ds) // owner, with no thread owner: born suspended
        } catch (e: Exception) {
            throw TransactionSystemException("Failed to open remote transaction", e)
        }
        val txId = UUID.randomUUID().toString()
        registry[txId] = Entry(scope, ownerKey, quotaKey, clock())
        trackOwner(ownerKey, quotaKey)
        begunCount.incrementAndGet()
        LOG.debug("remote tx opened: {} (owner={}, open={})", txId, ownerKey, registry.size)
        return txId
    }

    override fun resume(txId: String, ownerKey: String?) {
        val entry = registry[txId] ?: throw unknown(txId)
        verifyOwner(entry.owner, ownerKey)
        synchronized(entry) {
            if (entry.finished) throw unknown(txId)
            if (!entry.inUse.compareAndSet(false, true)) {
                throw TransactionConflictException("Remote transaction in use by another request: $txId")
            }
        }
        try {
            TransactionScope.resume(entry.scope)
            entry.lastUsedAt = clock()
        } catch (e: RuntimeException) {
            entry.inUse.set(false)
            throw e
        }
    }

    override fun suspend(txId: String) {
        val entry = registry[txId] ?: closingInUse[txId]
        try {
            // always: even without an entry, the thread must not keep the scope bound
            TransactionScope.suspend()
        } finally {
            if (entry != null) {
                entry.lastUsedAt = clock()
                val closeNow = synchronized(entry) {
                    entry.inUse.set(false)
                    entry.finished && closingInUse.remove(txId, entry)
                }
                // rolled back while this request was using it: close now, already unbound from the thread
                if (closeNow) rollbackQuietly(entry)
            }
        }
    }

    override fun commit(txId: String, ownerKey: String?) = finish(txId, ownerKey, rollback = false)

    override fun rollback(txId: String, ownerKey: String?) = finish(txId, ownerKey, rollback = true)

    override fun status(txId: String, ownerKey: String?): String {
        registry[txId]?.let { entry ->
            verifyOwner(entry.owner, ownerKey)
            return "open"
        }
        outcomes[txId]?.let { prior ->
            verifyOwner(prior.owner, ownerKey)
            return if (prior.committed) "committed" else "rolledback"
        }
        return "unknown"
    }

    override fun exists(txId: String?): Boolean = txId != null && registry.containsKey(txId)

    override fun hasOpenTransactionForOwner(ownerKey: String?): Boolean =
        ownerKey != null && openByOwner.containsKey(ownerKey)

    override fun stats(): RemoteTransactionStats = RemoteTransactionStats(
        openNow = registry.size,
        ownersWithOpen = openByOwner.size,
        retainedOutcomes = outcomes.size,
        begun = begunCount.get(),
        committed = committedCount.get(),
        rolledBack = rolledBackCount.get(),
        reaped = reapedCount.get(),
        rejectedByLimit = rejectedByLimitCount.get(),
    )

    // -------------------------------------------------------------------------

    /**
     * Rejects the opening if a cap is reached. **Soft** limit: under high concurrency it may be slightly
     * exceeded (non-atomic check), which is acceptable — the goal is to protect the pool, not to count exactly.
     */
    private fun enforceLimits(ownerKey: String?) {
        if (registry.size >= options.maxOpen) {
            rejectedByLimitCount.incrementAndGet()
            LOG.warn("GLOBAL remote transaction cap reached ({}) — opening rejected", options.maxOpen)
            throw TransactionLimitExceededException(
                "Global limit of open remote transactions reached (${options.maxOpen})"
            )
        }
        if (ownerKey != null && (openByQuota[ownerKey] ?: 0) >= options.maxOpenPerOwner) {
            rejectedByLimitCount.incrementAndGet()
            LOG.warn("PER-OWNER remote transaction cap reached ({}) — owner={}", options.maxOpenPerOwner, ownerKey)
            throw TransactionLimitExceededException(
                "Per-owner limit of open remote transactions reached (${options.maxOpenPerOwner})"
            )
        }
    }

    private fun trackOwner(owner: String?, quota: String?) {
        if (owner != null) openByOwner.merge(owner, 1, Int::plus)
        if (quota != null) openByQuota.merge(quota, 1, Int::plus)
    }

    private fun untrackOwner(entry: Entry) {
        entry.owner?.let { owner -> openByOwner.computeIfPresent(owner) { _, count -> if (count <= 1) null else count - 1 } }
        entry.quota?.let { quota -> openByQuota.computeIfPresent(quota) { _, count -> if (count <= 1) null else count - 1 } }
    }

    private fun finish(txId: String, ownerKey: String?, rollback: Boolean) {
        val entry = registry[txId]
        if (entry != null) {
            verifyOwner(entry.owner, ownerKey)
            val action = synchronized(entry) {
                when {
                    entry.finished -> Finish.ALREADY
                    entry.inUse.get() -> {
                        // A request is using the transaction now (the client gave up on it by timeout, for example).
                        // Closing the connection under the running statement would make it fail in some arbitrary
                        // way: the commit is refused, and the rollback counts as the outcome now, but closes when
                        // the request unbinds.
                        if (!rollback) {
                            throw TransactionConflictException("Remote transaction in use by a request: $txId; try again")
                        }
                        entry.finished = true
                        registry.remove(txId, entry)
                        closingInUse[txId] = entry
                        Finish.DEFERRED
                    }
                    else -> {
                        entry.finished = true
                        registry.remove(txId, entry)
                        Finish.NOW
                    }
                }
            }
            when (action) {
                Finish.NOW -> {
                    untrackOwner(entry)
                    closeAndRecord(txId, entry, rollback)
                    return
                }
                Finish.DEFERRED -> {
                    untrackOwner(entry)
                    outcomes[txId] = Finalized(false, entry.owner, clock())
                    rolledBackCount.incrementAndGet()
                    LOG.debug("remote tx rolled back while in use; closes when the request unbinds: {}", txId)
                    return
                }
                Finish.ALREADY -> Unit // another thread finished it: treat as a repetition
            }
        }
        finishAlreadyFinalized(txId, ownerKey, rollback)
    }

    /** Closes the scope (commit/rollback) and records the outcome for idempotency. */
    private fun closeAndRecord(txId: String, entry: Entry, rollback: Boolean) {
        // Commit requested, but a request failed inside the transaction (or someone marked rollback): roll back
        // and say so, instead of answering success to whoever thinks they committed.
        val condemned = !rollback && entry.scope.isRollbackOnly()
        if (condemned) {
            closeAndRecord(txId, entry, rollback = true)
            throw TransactionConflictException(
                "Transaction $txId rolled back: a request inside it failed (or it was marked for rollback)"
            )
        }
        try {
            if (rollback) {
                entry.scope.setRollbackOnly()
            }
            // closes without rebinding to the thread: in JDBC the commit has no thread affinity (with JTA, rebind first)
            entry.scope.close() // owner: commit (or rollback if marked) and close the connection
        } catch (e: Exception) {
            throw TransactionSystemException("Failed to finish remote transaction $txId", e)
        }
        outcomes[txId] = Finalized(!rollback, entry.owner, clock())
        (if (rollback) rolledBackCount else committedCount).incrementAndGet()
        LOG.debug("remote tx finished ({}): {} (open={})", if (rollback) "rollback" else "commit", txId, registry.size)
        runAfterCommit(entry.scope)
    }

    /**
     * The `afterCommit` tasks scheduled inside the remote transaction, if it committed. They run here, with the
     * connection already returned and no transaction bound to the thread: a task that needs one opens its own.
     */
    private fun runAfterCommit(scope: TransactionScope) {
        val tasks = scope.takeAfterCommit()
        if (tasks.isEmpty()) return
        val bound = TransactionScope.suspend()
        try {
            runBlocking { AfterCommitTasks.runAll(tasks) }
        } finally {
            TransactionScope.resume(bound)
        }
    }

    /** Repetition: the transaction is no longer open. Idempotent if the previous outcome matches the request. */
    private fun finishAlreadyFinalized(txId: String, ownerKey: String?, rollback: Boolean) {
        val prior = outcomes[txId] ?: throw unknown(txId)
        verifyOwner(prior.owner, ownerKey)
        if (prior.committed == rollback) {
            throw TransactionConflictException(
                "Transaction $txId already finished as ${if (prior.committed) "committed" else "rolledback"}; " +
                    "the opposite outcome is not accepted"
            )
        }
        // same outcome already applied → successful no-op (previous response lost)
    }

    private fun reapExpired() {
        val now = clock()
        if (registry.isNotEmpty()) {
            val idleDeadline = now - options.idleTimeoutMs
            for ((txId, entry) in registry) {
                val idleExpired = entry.lastUsedAt < idleDeadline
                val lifetimeExpired = (now - entry.createdAt) >= options.maxLifetimeMs
                val reap = (idleExpired || lifetimeExpired) && synchronized(entry) {
                    if (entry.finished || entry.inUse.get()) {
                        false
                    } else {
                        entry.finished = true
                        registry.remove(txId, entry)
                    }
                }
                if (reap) {
                    untrackOwner(entry)
                    rollbackQuietly(entry)
                    outcomes[txId] = Finalized(false, entry.owner, now) // reaped = rolledback (disambiguates the repetition)
                    reapedCount.incrementAndGet()
                    LOG.info(
                        "abandoned remote tx rolled back ({}): {} owner={}",
                        if (lifetimeExpired) "lifetime" else "idle", txId, entry.owner,
                    )
                }
            }
        }
        if (outcomes.isNotEmpty()) {
            val outcomeDeadline = now - options.outcomeRetentionMs
            outcomes.entries.removeIf { it.value.at < outcomeDeadline }
        }
    }

    private fun rollbackQuietly(entry: Entry) {
        try {
            entry.scope.setRollbackOnly()
            entry.scope.close()
        } catch (_: Exception) {
            // best effort — abandoned transaction
        }
    }

    private fun unknown(txId: String) = RemoteTransactionNotFoundException("Unknown or expired remote transaction: $txId")

    /** If the transaction has an owner (non-null key at begin), requires the operation to present the same key. */
    private fun verifyOwner(owner: String?, requesterKey: String?) {
        if (owner != null && owner != requesterKey) {
            throw AccessDeniedException("Remote transaction does not belong to the caller")
        }
    }

    private class Entry(
        val scope: TransactionScope,
        /** Opaque owner key. `null` = transaction without owner (security disabled). */
        val owner: String?,
        /** Per-owner cap key (the owner's, unless [begin] was given a `quotaKey`). */
        val quota: String?,
        val createdAt: Long,
    ) {
        val inUse = AtomicBoolean(false)

        /** Finished (or with deferred rollback): accepts no more requests. Touch only under the entry's lock. */
        var finished = false

        @Volatile
        var lastUsedAt: Long = createdAt
    }

    private enum class Finish { NOW, DEFERRED, ALREADY }

    /** Retained outcome of a finished transaction. */
    private class Finalized(val committed: Boolean, val owner: String?, val at: Long)

    private companion object {
        val LOG = Log.getLogger("RemoteTransactionCoordinatorImpl")
    }
}
