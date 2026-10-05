package br.com.wdc.shopping.view.react.controller

import br.com.wdc.shopping.view.react.skeleton.viewimpl.ApplicationReactImpl
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.javalin.http.Context
import java.util.concurrent.atomic.AtomicLong

/**
 * Exposes server-side metrics for the stress test tool.
 * Endpoint: GET /api/stress/metrics
 */
object StressMetricsController {

    private val GSON: Gson = GsonBuilder().create()
    private val totalRequests = AtomicLong(0)
    private val totalResponseTimeNs = AtomicLong(0)

    /**
     * Call this on every WebSocket request to track request count and latency.
     */
    fun recordRequest(responseTimeNs: Long) {
        totalRequests.incrementAndGet()
        totalResponseTimeNs.addAndGet(responseTimeNs)
    }

    fun handle(ctx: Context) {
        val runtime = Runtime.getRuntime()
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val heapMax = runtime.maxMemory() / (1024 * 1024)
        val threadCount = Thread.activeCount()

        val requests = totalRequests.get()
        val avgResponseMs = if (requests > 0) {
            (totalResponseTimeNs.get().toDouble() / requests) / 1_000_000.0
        } else {
            0.0
        }

        val metrics = mapOf(
            "activeSessions" to ApplicationReactImpl.getActiveSessionCount(),
            "heapUsedMb" to heapUsed,
            "heapMaxMb" to heapMax,
            "threadCount" to threadCount,
            "totalRequests" to requests,
            "avgResponseMs" to avgResponseMs,
        )

        ctx.contentType("application/json")
        ctx.result(GSON.toJson(metrics))
    }

    fun reset() {
        totalRequests.set(0)
        totalResponseTimeNs.set(0)
    }
}
