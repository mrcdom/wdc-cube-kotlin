package br.com.wdc.framework.rest.transaction

import br.com.wdc.framework.persistence.transaction.RemoteTransactionCoordinatorImpl
import br.com.wdc.framework.persistence.transaction.RemoteTransactionOptions
import br.com.wdc.framework.persistence.transaction.TransactionScope
import br.com.wdc.framework.persistence.transaction.TransactionServiceImpl
import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.testing.RestTestServer
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.h2.jdbcx.JdbcDataSource

class RestTransactionsTest {

    private val dataSource = JdbcDataSource().apply {
        setURL("jdbc:h2:mem:rest-tx-${UUID.randomUUID()};DB_CLOSE_DELAY=-1")
        user = "sa"
        password = "sa"
    }.also { ds -> ds.connection.use { it.createStatement().execute("CREATE TABLE T (ID INT PRIMARY KEY)") } }

    private val coordinator = RemoteTransactionCoordinatorImpl({ dataSource }, RemoteTransactionOptions.defaults())

    private fun insert(id: Int) {
        val connection = TransactionScope.current()?.connection() ?: error("no transaction")
        connection.prepareStatement("INSERT INTO T (ID) VALUES (?)").use {
            it.setInt(1, id)
            it.executeUpdate()
        }
    }

    private fun ids(): List<Int> = dataSource.connection.use { idsOn(it) }

    private fun idsOn(c: java.sql.Connection): List<Int> =
        c.createStatement().executeQuery("SELECT ID FROM T ORDER BY ID").use { rs -> buildList { while (rs.next()) add(rs.getInt(1)) } }

    /** A read as a repository does it: on the current transaction's connection, if there is one. */
    private fun idsInCurrentTxOrCommitted(): List<Int> = TransactionScope.current()?.connection()?.let { idsOn(it) } ?: ids()

    private val api = RestApi {
        transactions {
            service = TransactionServiceImpl { dataSource }
            coordinator = this@RestTransactionsTest.coordinator
        }
        post("/rows/{id}", transactional = true) { call ->
            insert(call.pathInt("id"))
            insert(call.pathInt("id") + 1000)
            if (call.query("fail") != null) throw IllegalStateException("failed after writing")
            mapOf("ok" to true)
        }
        get("/rows") { call -> call.inRemoteTransaction { idsInCurrentTxOrCommitted() } }
        RemoteTransactionRoutes.register(this, transactions)
    }

    private val server = RestTestServer(api)

    @AfterTest
    fun stop() {
        server.close()
    }

    @Test
    fun theHeader_onAServerWithoutRemoteTransactions_isRefused() {
        // the client believes it is in a transaction: a write made outside it could not be undone
        val plain = RestApi {
            transactions { service = TransactionServiceImpl { dataSource } }
            post("/rows/{id}", transactional = true) { call -> insert(call.pathInt("id")); mapOf("ok" to true) }
            get("/rows") { call -> call.inRemoteTransaction { idsInCurrentTxOrCommitted() } }
        }
        RestTestServer(plain).use { server ->
            assertEquals(400, server.client().header("X-Tx-Id", "abc").post("/rows/1").status)
            assertEquals(400, server.client().header("X-Tx-Id", "abc").get("/rows").status)
            assertEquals(emptyList(), ids(), "nothing written")
            // without the header, as before
            assertEquals(200, server.client().post("/rows/2").status)
            assertEquals(200, server.client().get("/rows").status)
        }
    }

    @Test
    fun perRequest_commitsOnSuccess_andRollsBackOnFailure_withoutTheHeader() {
        assertEquals(200, server.client().post("/rows/1").status)
        assertEquals(500, server.client().post("/rows/2?fail=1").status)
        assertEquals(listOf(1, 1001), ids())
    }

    @Test
    fun remoteTransaction_joinsTheWrites_andTheClientDecides() {
        val client = server.client().header("X-Client-Id", "c1")
        val txId = client.post("/api/tx/begin").json().asJsonObject["txId"].asString
        client.header("X-Tx-Id", txId)
        assertEquals(200, client.post("/rows/5").status)
        assertEquals(200, client.post("/rows/6").status)
        assertEquals(emptyList(), ids(), "nothing committed before the commit")

        // the same owner, without the header, with the transaction open: refused
        assertEquals(409, server.client().header("X-Client-Id", "c1").post("/rows/7").status)
        // another owner is not affected
        assertEquals(200, server.client().header("X-Client-Id", "c2").post("/rows/8").status)

        assertEquals("""{"status":"open"}""", client.get("/api/tx/status").body)
        assertEquals("""{"status":"committed"}""", client.post("/api/tx/commit").body)
        assertEquals(listOf(5, 6, 8, 1005, 1006, 1008), ids())
    }

    @Test
    fun readWithTheHeader_seesTheTransactionsOwnWrites() {
        val client = server.client().header("X-Client-Id", "c1")
        val txId = client.post("/api/tx/begin").json().asJsonObject["txId"].asString
        client.header("X-Tx-Id", txId)
        client.post("/rows/5")

        assertEquals("[5,1005]", client.get("/rows").body, "inside the transaction")
        assertEquals("[]", server.client().header("X-Client-Id", "c2").get("/rows").body, "outside it, only what is committed")
        // a read does not hold the transaction: the commit right after is not "in use"
        assertEquals("""{"status":"committed"}""", client.post("/api/tx/commit").body)
        assertEquals("[5,1005]", server.client().get("/rows").body)
    }

    @Test
    fun remoteTransaction_rollback_andForeignOwner() {
        val client = server.client().header("X-Client-Id", "c1")
        val txId = client.post("/api/tx/begin").json().asJsonObject["txId"].asString
        client.header("X-Tx-Id", txId)
        client.post("/rows/9")
        assertEquals(403, server.client().header("X-Client-Id", "intruso").header("X-Tx-Id", txId).post("/api/tx/commit").status)
        // a request without the header is malformed (400), not a refusal of access (403)
        for (op in listOf("commit", "rollback")) {
            assertEquals(400, server.client().post("/api/tx/$op").status, "$op without X-Tx-Id")
        }
        assertEquals(400, server.client().get("/api/tx/status").status, "status without X-Tx-Id")
        assertEquals("""{"status":"rolledback"}""", client.post("/api/tx/rollback").body)
        assertEquals(emptyList(), ids())
    }
}
