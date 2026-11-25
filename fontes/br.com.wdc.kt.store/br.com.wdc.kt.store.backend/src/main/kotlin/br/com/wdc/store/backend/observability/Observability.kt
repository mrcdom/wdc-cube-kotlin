package br.com.wdc.store.backend.observability

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.util.Defer
import br.com.wdc.framework.cube.remote.RemoteApplicationRegistry
import br.com.wdc.framework.persistence.transaction.RemoteTransactionCoordinator
import br.com.wdc.store.backend.cluster.StoreCluster
import br.com.wdc.store.domain.security.AuthenticationService
import br.com.wdc.store.persistence.security.AuthenticationServiceImpl
import com.google.gson.Gson
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * The backend's metrics and health checks.
 *
 * **Metrics**, in Prometheus format at `GET /metrics`, only when `management.token` is set, and only with
 * `Authorization: Bearer <token>`: the JVM (memory, GC, threads, CPU, uptime), every HTTP request by method, route and
 * status (`http.server.requests`), and the WDC Store's own — sign-ins by outcome, sessions (live here, renewed, ended
 * elsewhere), the remote views (live sessions, each message's handling), the REST API's remote transactions, and the
 * mesh between instances. What the lower layers count goes to Micrometer's global registry, to which this one is added;
 * the framework's counts (remote transactions, remote views) are read from what it exposes.
 *
 * **Health**, open, for load balancers: `GET /health/live` (the process answers; also `/health`) and
 * `GET /health/ready` (the database answers; 503 if not; with the mesh, how many peers are connected — reported, not
 * required, since an instance alone still serves).
 *
 * @param token the token `/metrics` asks for; `null` keeps the route out
 */
class Observability(
    private val token: String?,
    private val dataSource: DataSource,
    private val remoteApps: RemoteApplicationRegistry,
    private val transactions: () -> RemoteTransactionCoordinator?,
    private val cluster: () -> StoreCluster?,
) {

    companion object {
        private val LOG = Log.getLogger("Observability")
        private const val STARTED = "observability.started"
        private const val CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8"
        private val GSON = Gson()

        private fun Context.answer(status: Int, body: Map<String, Any>) {
            status(status).contentType("application/json").result(GSON.toJson(body))
        }
    }

    private val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    /** Adds the registry to the global one and binds the meters; [cleanUp] takes them out again. */
    fun start(cleanUp: Defer) {
        Metrics.globalRegistry.add(registry)
        cleanUp.push {
            Metrics.globalRegistry.remove(registry)
            registry.close()
        }
        listOf(ClassLoaderMetrics(), JvmMemoryMetrics(), JvmGcMetrics(), JvmThreadMetrics(), ProcessorMetrics(), UptimeMetrics())
            .forEach { it.bindTo(registry) }
        bindSessions(registry)
        bindRemoteViews(registry, cleanUp)
        bindRemoteTransactions(registry)
        bindCluster(registry)
        if (token == null) LOG.info("Metrics off: management.token is not set")
    }

    // :: Meters

    private fun bindSessions(registry: MeterRegistry) {
        Gauge.builder("store.sessions.live") { (AuthenticationService.BEAN.getOrNull() as? AuthenticationServiceImpl)?.liveSessions ?: 0 }
            .description("User sessions this instance holds in memory").register(registry)
    }

    private fun bindRemoteViews(registry: MeterRegistry, cleanUp: Defer) {
        Gauge.builder("store.remote.sessions", remoteApps) { it.activeSessionCount.toDouble() }
            .description("Remote view sessions alive on this instance").register(registry)
        val ok = Timer.builder("store.remote.messages").description("Remote view messages handled")
            .tag("outcome", "ok").register(registry)
        val failed = Timer.builder("store.remote.messages").description("Remote view messages handled")
            .tag("outcome", "failed").register(registry)
        val listening = remoteApps.onMessage { nanos, isFailure ->
            (if (isFailure) failed else ok).record(nanos, TimeUnit.NANOSECONDS)
        }
        cleanUp.push { listening.close() }
    }

    private fun bindRemoteTransactions(registry: MeterRegistry) {
        Gauge.builder("store.remote.transactions.open") { transactions()?.stats()?.openNow ?: 0 }
            .description("REST remote transactions open now").register(registry)
        val events = mapOf<String, (br.com.wdc.framework.persistence.transaction.RemoteTransactionStats) -> Long>(
            "begun" to { it.begun },
            "committed" to { it.committed },
            "rolledback" to { it.rolledBack },
            "reaped" to { it.reaped },
            "refused" to { it.rejectedByLimit },
        )
        for ((event, read) in events) {
            FunctionCounter.builder("store.remote.transactions", this) { transactions()?.stats()?.let(read)?.toDouble() ?: 0.0 }
                .description("REST remote transactions, by what happened to them").tag("event", event).register(registry)
        }
    }

    private fun bindCluster(registry: MeterRegistry) {
        Gauge.builder("store.cluster.peers") { cluster()?.mesh?.connectedPeers?.size ?: 0 }
            .description("Other instances connected to this one").register(registry)
        Gauge.builder("store.cluster.links.ready") { cluster()?.mesh?.readyLinks ?: 0 }
            .description("Connections from this instance to others, ready").register(registry)
    }

    // :: Routes

    /** Times every request, and adds `/metrics` and the health checks. */
    fun configure(config: JavalinConfig) {
        config.routes.before { ctx -> ctx.attribute(STARTED, System.nanoTime()) }
        config.routes.after { ctx -> record(ctx) }

        if (token != null) config.routes.get("/metrics") { ctx -> metrics(ctx) }
        config.routes.get("/health") { ctx -> ctx.answer(200, mapOf("status" to "UP")) }
        config.routes.get("/health/live") { ctx -> ctx.answer(200, mapOf("status" to "UP")) }
        config.routes.get("/health/ready") { ctx -> ready(ctx) }
    }

    private fun record(ctx: Context) {
        val started = ctx.attribute<Long>(STARTED) ?: return
        // the route as declared (`/api/repo/product/{id}/image`), not the address, so that the series stay few
        val route = try {
            ctx.endpoints().matchedHttpEndpoint()?.path
        } catch (_: Exception) {
            null
        } ?: "unmatched"
        Timer.builder("http.server.requests")
            .tag("method", ctx.method().name)
            .tag("route", route)
            .tag("status", ctx.statusCode().toString())
            .register(registry)
            .record(System.nanoTime() - started, TimeUnit.NANOSECONDS)
    }

    private fun metrics(ctx: Context) {
        // `Bearer <token>`, the scheme in any case
        val header = ctx.header("Authorization")?.trim() ?: ""
        val given = if (header.length > 7 && header.substring(0, 7).equals("Bearer ", ignoreCase = true)) header.substring(7).trim() else null
        val expected = token ?: ""
        if (given == null || !MessageDigest.isEqual(given.toByteArray(StandardCharsets.UTF_8), expected.toByteArray(StandardCharsets.UTF_8))) {
            ctx.header("WWW-Authenticate", "Bearer")
            ctx.answer(401, mapOf("error" to "A valid token is required"))
            return
        }
        ctx.res().contentType = CONTENT_TYPE
        ctx.result(registry.scrape())
    }

    private fun ready(ctx: Context) {
        val database = try {
            dataSource.connection.use { it.isValid(2) }
        } catch (_: Exception) {
            false
        }
        val body = linkedMapOf<String, Any>(
            "status" to if (database) "UP" else "DOWN",
            "database" to if (database) "UP" else "DOWN",
        )
        cluster()?.mesh?.let { mesh -> body["cluster"] = mapOf("peers" to mesh.connectedPeers.size, "links" to mesh.readyLinks) }
        ctx.answer(if (database) 200 else 503, body)
    }
}
