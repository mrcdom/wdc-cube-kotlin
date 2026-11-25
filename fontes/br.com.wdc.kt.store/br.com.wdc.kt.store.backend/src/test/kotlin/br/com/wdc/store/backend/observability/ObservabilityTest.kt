package br.com.wdc.store.backend.observability

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.log.Slf4jLogFactory
import br.com.wdc.framework.commons.util.Defer
import br.com.wdc.framework.cube.remote.RemoteApplicationRegistry
import io.javalin.Javalin
import org.h2.jdbcx.JdbcDataSource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** `/metrics` asks for the token, and only exists with one; the health checks tell whether the database answers. */
class ObservabilityTest {

    init {
        Log.setFactory(Slf4jLogFactory())
    }

    private val cleanUp = Defer()
    private val servers = ArrayList<Javalin>()
    private val http = HttpClient.newHttpClient()

    @AfterTest
    fun stop() {
        servers.forEach { it.stop() }
        cleanUp.run()
    }

    private val database = JdbcDataSource().apply { setURL("jdbc:h2:mem:observability;DB_CLOSE_DELAY=-1") }

    /** A data source whose database does not answer. */
    private val noDatabase = object : DataSource by database {
        override fun getConnection(): Connection = throw SQLException("down")
    }

    private fun server(token: String?, dataSource: DataSource = database): String {
        val observability = Observability(
            token = token,
            dataSource = dataSource,
            remoteApps = RemoteApplicationRegistry { error("no sessions here") },
            transactions = { null },
            cluster = { null },
        )
        observability.start(cleanUp)
        val server = Javalin.create { observability.configure(it) }.start("127.0.0.1", 0)
        servers += server
        return "http://127.0.0.1:${server.port()}"
    }

    private fun get(url: String, token: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI(url)).GET()
        if (token != null) request.header("Authorization", "Bearer $token")
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun theMetrics_needTheToken() {
        val base = server(token = "metrics-token")
        get("$base/health/live")

        assertEquals(401, get("$base/metrics").statusCode())
        assertEquals(401, get("$base/metrics", token = "wrong").statusCode())
        val bare = HttpRequest.newBuilder(URI("$base/metrics")).header("Authorization", "metrics-token").GET().build()
        assertEquals(401, http.send(bare, HttpResponse.BodyHandlers.ofString()).statusCode(), "the scheme is required")
        val lower = HttpRequest.newBuilder(URI("$base/metrics")).header("Authorization", "bearer metrics-token").GET().build()
        assertEquals(200, http.send(lower, HttpResponse.BodyHandlers.ofString()).statusCode(), "in any case")

        val metrics = get("$base/metrics", token = "metrics-token")
        assertEquals(200, metrics.statusCode())
        assertContains(metrics.headers().firstValue("Content-Type").orElse(""), "text/plain")
        val text = metrics.body()
        assertContains(text, """http_server_requests_seconds_count{method="GET",route="/health/live",status="200"}""")
        assertContains(text, "jvm_memory_used_bytes")
        assertContains(text, "store_remote_sessions")
        assertContains(text, "store_remote_transactions_total")
        assertContains(text, "store_cluster_peers")
    }

    @Test
    fun withoutAToken_thereAreNoMetrics() {
        val base = server(token = null)
        assertEquals(404, get("$base/metrics").statusCode())
        assertEquals(404, get("$base/metrics", token = "").statusCode())
    }

    @Test
    fun readiness_followsTheDatabase() {
        val up = server(token = null)
        assertEquals(200, get("$up/health/live").statusCode())
        assertEquals(200, get("$up/health").statusCode())
        val ready = get("$up/health/ready")
        assertEquals(200, ready.statusCode())
        assertContains(ready.body(), "\"database\":\"UP\"")
        assertFalse("cluster" in ready.body(), "no mesh, nothing about it")

        val down = server(token = null, dataSource = noDatabase)
        assertEquals(200, get("$down/health/live").statusCode(), "the process still answers")
        val notReady = get("$down/health/ready")
        assertEquals(503, notReady.statusCode())
        assertContains(notReady.body(), "\"database\":\"DOWN\"")
    }
}
