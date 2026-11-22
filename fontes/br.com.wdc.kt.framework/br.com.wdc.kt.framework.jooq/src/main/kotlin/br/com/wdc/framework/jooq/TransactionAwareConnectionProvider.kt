package br.com.wdc.framework.jooq

import br.com.wdc.framework.persistence.transaction.TransactionScope
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import org.jooq.ConnectionProvider
import org.jooq.exception.DataAccessException

/**
 * jOOQ [ConnectionProvider] aware of [TransactionScope] — the piece that makes repositories take part in the
 * transaction without knowing about it.
 *
 * - **Inside a transaction** ([TransactionScope.current] non-null): hands out the current scope's connection,
 *   so that all queries in the block share the same physical transaction. Does not close the connection in
 *   [release] — the scope is the owner.
 * - **Outside a transaction**: borrows a standalone connection from the [DataSource] (autocommit) and returns it in [release].
 */
class TransactionAwareConnectionProvider(private val dataSource: DataSource) : ConnectionProvider {

    override fun acquire(): Connection {
        TransactionScope.current()?.let { return it.connection() }
        try {
            return dataSource.connection
        } catch (e: SQLException) {
            throw DataAccessException("Could not get a connection from the DataSource", e)
        }
    }

    override fun release(connection: Connection) {
        // acquire()/release() wrap a single execution on the same thread; the scope's connection is recognized by
        // identity, so a standalone one borrowed before the transaction began is still returned.
        if (TransactionScope.current()?.connection() === connection) {
            return
        }
        try {
            connection.close()
        } catch (e: SQLException) {
            throw DataAccessException("Failed to release standalone connection", e)
        }
    }
}
