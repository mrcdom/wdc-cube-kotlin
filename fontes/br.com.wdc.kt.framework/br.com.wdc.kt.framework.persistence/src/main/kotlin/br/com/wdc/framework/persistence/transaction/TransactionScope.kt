package br.com.wdc.framework.persistence.transaction

import br.com.wdc.framework.domain.transaction.AfterCommitTasks
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.asContextElement

/**
 * JDBC transaction frame bound to the thread (via [ThreadLocal]).
 *
 * **Reentrancy (REQUIRED propagation):** [begin] joins the active transaction when there is one —
 * **sharing the owner's connection** (a single physical transaction) — or opens a new one and becomes the
 * owner. Only the owner commits/rolls back and closes the connection in [close]; for participants [close] is a no-op.
 *
 * **Suspension (REQUIRES_NEW / NOT_SUPPORTED):** [suspend] unbinds the current transaction from the thread (the
 * connection/transaction stays alive, with no owner on the thread) and [resume] rebinds it. A new scope opened in
 * that interval uses a **second connection** from the pool, independent of the suspended one.
 *
 * Used by [TransactionServiceImpl] (CMT style), by the remote transaction coordinator and by the
 * repositories, which get the current transaction's connection via [current].
 *
 * **Coroutines:** the scope lives in a `ThreadLocal` because whoever reads it is blocking code (jOOQ's connection
 * provider), which receives no context. For a `suspend` block, [asContextElement] carries the scope along with
 * the coroutine: on each resumption, on any thread, the `ThreadLocal` is reinstalled.
 *
 * **JDBC only.** The shape already separates the three points a JTA mode would need to touch, without changing
 * the contract: the decision of how to open lives in [open]; commit/rollback, in the owner's [close]; and
 * binding/unbinding from the thread, in [suspend]/[resume] (with JTA, the transaction gets thread affinity, and
 * the transactional block can no longer switch threads).
 */
class TransactionScope private constructor(
    private val connection: Connection,
    /** Scope that holds the physical transaction; `this` when owner. */
    owner: TransactionScope?,
    /** Original `autoCommit`, restored in the owner's [close]. */
    private val oldAutoCommit: Boolean,
) : AutoCloseable {

    private val root: TransactionScope = owner ?: this

    private val isOwner: Boolean get() = root === this

    /** Rollback requested ([setRollbackOnly]): the owner rolls back without throwing. */
    @Volatile
    private var rollbackOnly = false

    /** Doomed by a participant that failed ([markFailed]): the owner rolls back and its caller is told. */
    @Volatile
    private var failed = false

    @Volatile
    private var closed = false

    /** Tasks for after the commit; only the owner's count (participants hand them to it). */
    private val afterCommitTasks = AfterCommitTasks()

    @Volatile
    private var committed = false

    /**
     * The transaction's connection.
     *
     * @throws IllegalStateException if the scope is already closed
     */
    fun connection(): Connection {
        check(!closed) { "TransactionScope already closed" }
        return connection
    }

    /** Whether the owner's [close] will roll back: rollback requested or a participant failed. */
    fun isRollbackOnly(): Boolean = root.rollbackOnly || root.failed

    /** Marks the transaction for rollback (irreversible). The commit will not run in the owner's [close]. */
    fun setRollbackOnly() {
        root.rollbackOnly = true
    }

    /**
     * Dooms the transaction because a call inside it failed (irreversible). Like [setRollbackOnly], the owner's
     * [close] rolls back; unlike it, whoever opened the transaction is told ([TransactionServiceImpl] throws).
     */
    internal fun markFailed() {
        root.failed = true
    }

    /** Whether a call inside this physical transaction failed ([markFailed]). */
    internal fun isFailed(): Boolean = root.failed

    /** Whether this frame owns the physical transaction (commits or rolls back in [close]). */
    internal fun ownsTransaction(): Boolean = isOwner

    /**
     * Schedules [task] for after the commit of this physical transaction (the owner's, also when called on a
     * participant). Whoever closes the owner runs it, through [takeAfterCommit].
     */
    fun afterCommit(task: suspend () -> Unit) {
        check(!root.closed) { "TransactionScope already closed" }
        root.afterCommitTasks.add(task)
    }

    /**
     * The scheduled tasks, if the owner was closed with commit; empty if rolled back or still open. Drains the
     * queue: each task is delivered only once.
     */
    fun takeAfterCommit(): List<suspend () -> Unit> {
        if (!isOwner || !closed) return emptyList()
        val tasks = afterCommitTasks.drain()
        return if (committed) tasks else emptyList()
    }

    /**
     * Ends the frame. Participant: no-op (the connection belongs to the owner). Owner: commits (or rolls back, if
     * marked or doomed), restores `autoCommit` and closes the connection.
     */
    @Throws(SQLException::class)
    override fun close() {
        if (closed) {
            return
        }
        closed = true
        if (!isOwner) {
            return
        }
        if (CURRENT.get() === this) {
            CURRENT.remove()
        }
        try {
            if (rollbackOnly || failed) {
                connection.rollback()
            } else {
                connection.commit()
                committed = true
            }
        } catch (ex: SQLException) {
            try {
                connection.rollback()
            } catch (suppressed: SQLException) {
                ex.addSuppressed(suppressed)
            }
            throw ex
        } finally {
            try {
                connection.autoCommit = oldAutoCommit
            } catch (_: SQLException) {
                // best effort
            }
            try {
                connection.close()
            } catch (_: Exception) {
                // best effort
            }
        }
    }

    companion object {
        private val CURRENT = ThreadLocal<TransactionScope?>()

        // :: REQUIRED propagation

        /**
         * Joins the active transaction (participant sharing the owner's connection) or opens a new one
         * (owner, installed as current).
         */
        @Throws(SQLException::class)
        fun begin(dataSource: DataSource): TransactionScope {
            return current()?.participant() ?: beginNew(dataSource)
        }

        /**
         * Unconditionally opens a new owner transaction and installs it as current. Must be preceded by
         * [suspend] if a transaction is already active (REQUIRES_NEW case).
         */
        @Throws(SQLException::class)
        fun beginNew(dataSource: DataSource): TransactionScope {
            val scope = open(dataSource)
            CURRENT.set(scope)
            return scope
        }

        /**
         * Opens a new owner transaction **without** installing it on the thread. Single decision point for how
         * the physical transaction is born.
         */
        @Throws(SQLException::class)
        internal fun open(dataSource: DataSource): TransactionScope {
            val connection = dataSource.connection
            try {
                val oldAutoCommit = connection.autoCommit
                connection.autoCommit = false
                return TransactionScope(connection, null, oldAutoCommit)
            } catch (e: SQLException) {
                try {
                    connection.close()
                } catch (suppressed: Exception) {
                    e.addSuppressed(suppressed)
                }
                throw e
            }
        }

        // :: Suspension / resumption (REQUIRES_NEW, NOT_SUPPORTED, remote transaction)

        /**
         * Unbinds the current transaction from the thread and returns it for a later [resume]. `null` if there is
         * no active transaction. The suspended transaction stays alive (it is not committed).
         */
        fun suspend(): TransactionScope? {
            val current = current()
            CURRENT.remove()
            return current
        }

        /** Rebinds a transaction previously suspended by [suspend]. No-op if [suspended] is `null`. */
        fun resume(suspended: TransactionScope?) {
            if (suspended != null) {
                CURRENT.set(suspended)
            }
        }

        // :: Access / state

        /** The thread's current scope, or `null` if there is no active transaction. */
        fun current(): TransactionScope? = CURRENT.get()?.takeUnless { it.closed }

        /** Checks whether there is an active transaction on the current thread. */
        fun isActive(): Boolean = current() != null

        /**
         * Context element that keeps [scope] as the coroutine's current transaction, on any thread where it is
         * resumed, and gives the thread back its previous value on leaving. `null` runs **without** a transaction,
         * even if the outer context carries one.
         */
        internal fun asContextElement(scope: TransactionScope?): CoroutineContext.Element = CURRENT.asContextElement(scope)
    }

    /** Participant of this transaction: shares the connection, with no lifecycle of its own. */
    internal fun participant(): TransactionScope = TransactionScope(connection, root, oldAutoCommit)
}
