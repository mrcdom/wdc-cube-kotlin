package br.com.wdc.framework.persistence.transaction

import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import org.h2.jdbcx.JdbcDataSource

/** H2 em memória com uma tabela `T(ID, NAME)`, e um DataSource que conta as conexões abertas. */
class TestDatabase(name: String) : AutoCloseable {

    private val h2 = JdbcDataSource().apply {
        setURL("jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1")
        user = "sa"
        password = "sa"
    }

    /** Conexões entregues e ainda não fechadas. */
    val openConnections = AtomicInteger()

    /** Total de conexões entregues. */
    val acquired = AtomicInteger()

    /** Quando `true`, o próximo commit falha. */
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
                        throw java.sql.SQLException("commit recusado (teste)")
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

    /** Insere pela transação corrente da thread. */
    fun insertInCurrentTx(id: Int, name: String = "n$id") {
        val connection = TransactionScope.current()?.connection() ?: error("sem transação corrente")
        insert(connection, id, name)
    }

    /** Insere por uma conexão avulsa, em autocommit. */
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

    /** Ids persistidos, vistos por uma conexão independente. */
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
