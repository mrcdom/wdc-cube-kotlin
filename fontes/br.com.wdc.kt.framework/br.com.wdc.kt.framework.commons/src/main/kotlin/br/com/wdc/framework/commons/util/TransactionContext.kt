package br.com.wdc.framework.commons.util

import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

/**
 * Gerenciador de transação local com propagação REQUIRED via [ThreadLocal].
 *
 * Uso típico:
 * ```
 * TransactionContext.begin(dataSource).use { tx ->
 *     // operações usando tx.connection()
 * }
 * // commit automático ao fechar; rollback em caso de exceção
 * ```
 *
 * Composição transacional (mesma conexão/transação):
 * ```
 * TransactionContext.begin(dataSource).use { tx ->
 *     shoppingService.purchase(userId, items)    // participa da tx existente
 *     shoppingService.loadPurchases(userId)       // mesma conexão
 * }
 * ```
 */
class TransactionContext private constructor(
    private val connection: Connection,
    private val owner: Boolean,
    private val oldAutoCommit: Boolean,
) : AutoCloseable {

    private var rollbackOnly = false
    private var closed = false

    /**
     * Obtém a conexão da transação corrente.
     *
     * @throws IllegalStateException se o contexto já foi fechado
     */
    fun connection(): Connection {
        check(!closed) { "TransactionContext already closed" }
        return connection
    }

    /**
     * Marca a transação para rollback.
     */
    fun setRollbackOnly() {
        if (owner) {
            rollbackOnly = true
        } else {
            CURRENT.get()?.rollbackOnly = true
        }
    }

    /**
     * Fecha o contexto:
     * - Owner: commit ou rollback, fecha conexão e limpa ThreadLocal.
     * - Participante: noop.
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
         * Inicia ou participa de uma transação (propagação REQUIRED).
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
         * Verifica se há uma transação ativa na thread atual.
         */
        @JvmStatic
        fun isActive(): Boolean {
            val current = CURRENT.get()
            return current != null && !current.closed
        }
    }
}
