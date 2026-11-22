package br.com.wdc.framework.commons.util

import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

/**
 * Local transaction manager with REQUIRED propagation via [ThreadLocal].
 *
 * Typical use:
 * ```
 * TransactionContext.begin(dataSource).use { tx ->
 *     // operations using tx.connection()
 * }
 * // automatic commit on close; rollback in case of exception
 * ```
 *
 * Transactional composition (same connection/transaction):
 * ```
 * TransactionContext.begin(dataSource).use { tx ->
 *     shoppingService.purchase(userId, items)    // joins the existing tx
 *     shoppingService.loadPurchases(userId)       // same connection
 * }
 * ```
 */
@Deprecated(
    "Only REQUIRED propagation, and it commits in case of exception. Use TransactionService " +
        "(br.com.wdc.framework.domain.transaction), implemented by TransactionServiceImpl in framework-persistence."
)
class TransactionContext private constructor(
    private val connection: Connection,
    private val owner: Boolean,
    private val oldAutoCommit: Boolean,
) : AutoCloseable {

    private var rollbackOnly = false
    private var closed = false

    /**
     * Gets the connection of the current transaction.
     *
     * @throws IllegalStateException if the context was already closed
     */
    fun connection(): Connection {
        check(!closed) { "TransactionContext already closed" }
        return connection
    }

    /**
     * Marks the transaction for rollback.
     */
    fun setRollbackOnly() {
        if (owner) {
            rollbackOnly = true
        } else {
            CURRENT.get()?.rollbackOnly = true
        }
    }

    /**
     * Closes the context:
     * - Owner: commit or rollback, closes the connection and clears the ThreadLocal.
     * - Participant: noop.
     */
    @Throws(SQLException::class)
    override fun close() {
        if (closed) return
        closed = true

        if (!owner) return

        CURRENT.remove()

        try {
            if (rollbackOnly) {
                connection.rollback()
            } else {
                connection.commit()
            }
        } catch (ex: SQLException) {
            try {
                connection.rollback()
            } catch (suppressed: SQLException) {
                ex.addSuppressed(suppressed)
            }
            throw ex
        } finally {
            connection.autoCommit = oldAutoCommit
            connection.close()
        }
    }

    companion object {
        private val CURRENT = ThreadLocal<TransactionContext>()

        /**
         * Starts or joins a transaction (REQUIRED propagation).
         */
        @JvmStatic
        @Throws(SQLException::class)
        fun begin(dataSource: DataSource): TransactionContext {
            val current = CURRENT.get()
            if (current != null && !current.closed) {
                return TransactionContext(current.connection, owner = false, current.oldAutoCommit)
            }

            val connection = dataSource.connection
            val oldAutoCommit = connection.autoCommit
            connection.autoCommit = false

            val ctx = TransactionContext(connection, owner = true, oldAutoCommit)
            CURRENT.set(ctx)
            return ctx
        }

        /**
         * Checks whether there is an active transaction on the current thread.
         */
        @JvmStatic
        fun isActive(): Boolean {
            val current = CURRENT.get()
            return current != null && !current.closed
        }
    }
}
