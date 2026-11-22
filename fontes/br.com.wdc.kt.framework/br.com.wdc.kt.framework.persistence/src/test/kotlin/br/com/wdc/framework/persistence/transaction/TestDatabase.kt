package br.com.wdc.framework.persistence.transaction

import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import org.h2.jdbcx.JdbcDataSource

/** In-memory H2 with a table `T(ID, NAME)`, and a DataSource that counts open connections. */
class TestDatabase(name: String) : AutoCloseable {

    private val h2 = JdbcDataSource().apply {
        setURL("jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1")
        user = "sa"
        password = "sa"
    }

    /** Connections handed out and not yet closed. */
    val openConnections = AtomicInteger()

    /** Total connections handed out. */
    val acquired = AtomicInteger()

    /** When `true`, the next commit fails. */
    @Volatile
    var failNextCommit = false

    val dataSource: DataSource = object : DataSource by h2 {
        override fun getConnection(): Connection = track(h2.connection)
    }

    init {
        h2.connection.use { it.createStatement().use { st -> st.execute("CREATE TABLE T (ID INT PRIMARY KEY, NAME VARCHAR(50))") } }
    }

    private fun track(real: Connection): Connection {
        acquired.incrementAndGet()
        openConnections.incrementAndGet()
        var closed = false
        return java.lang.reflect.Proxy.newProxyInstance(
            Connection::class.java.classLoader, arrayOf(Connection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "close" -> {
                    if (!closed) {
                        closed = true
                        openConnections.decrementAndGet()
                    }
                    real.close()
                    null
                }
                "commit" -> {
                    if (failNextCommit) {
                        failNextCommit = false
                        throw java.sql.SQLException("commit refused (test)")
                    }
                    real.commit()
                    null
                }
                else -> try {
                    method.invoke(real, *(args ?: emptyArray()))
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    throw e.targetException
                }
            }
        } as Connection
    }

    /** Inserts through the thread's current transaction. */
    fun insertInCurrentTx(id: Int, name: String = "n$id") {
        val connection = TransactionScope.current()?.connection() ?: error("no current transaction")
        insert(connection, id, name)
    }

    /** Inserts through a standalone connection, in autocommit. */
    fun insertAutoCommit(id: Int, name: String = "n$id") {
        dataSource.connection.use { insert(it, id, name) }
    }

    private fun insert(connection: Connection, id: Int, name: String) {
        connection.prepareStatement("INSERT INTO T (ID, NAME) VALUES (?, ?)").use {
            it.setInt(1, id)
            it.setString(2, name)
            it.executeUpdate()
        }
    }

    /** Persisted ids, seen through an independent connection. */
    fun ids(): List<Int> = h2.connection.use { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT ID FROM T ORDER BY ID").use { rs ->
                buildList { while (rs.next()) add(rs.getInt(1)) }
            }
        }
    }

    override fun close() {
        h2.connection.use { it.createStatement().use { st -> st.execute("SHUTDOWN") } }
    }
}
