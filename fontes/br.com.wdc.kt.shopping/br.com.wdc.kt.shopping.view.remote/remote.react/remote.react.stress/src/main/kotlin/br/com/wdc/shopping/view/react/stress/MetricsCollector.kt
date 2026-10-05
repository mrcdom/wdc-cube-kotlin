package br.com.wdc.shopping.view.react.stress

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Collects latency and throughput metrics for the stress test.
 * Thread-safe — multiple VirtualClients report concurrently.
 */
class MetricsCollector {

    private val actionLatencies = ConcurrentHashMap<String, MutableList<Long>>()
    private val totalActions = AtomicLong(0)
    private val totalErrors = AtomicLong(0)
    private val connectionTimes = mutableListOf<Long>()
    private val startTime = AtomicLong(0)
    private val endTime = AtomicLong(0)

    @Volatile
    var warmupComplete = false

    fun markStart() {
        startTime.set(System.currentTimeMillis())
    }

    fun markEnd() {
        endTime.set(System.currentTimeMillis())
    }

    @Synchronized
    fun recordConnectionTime(millis: Long) {
        if (warmupComplete) {
            connectionTimes.add(millis)
        }
    }

    fun recordAction(actionName: String, latencyMs: Long) {
        if (!warmupComplete) return
        totalActions.incrementAndGet()
        actionLatencies.computeIfAbsent(actionName) { mutableListOf() }
            .let { list ->
                synchronized(list) { list.add(latencyMs) }
            }
    }

    fun recordError(actionName: String) {
        if (!warmupComplete) return
        totalErrors.incrementAndGet()
    }

    fun generateReport(): StressReport {
        val elapsed = endTime.get() - startTime.get()
        val actions = totalActions.get()
        val errors = totalErrors.get()

        val actionStats = actionLatencies.map { (name, latencies) ->
            val sorted = synchronized(latencies) { latencies.sorted() }
            ActionStats(
                name = name,
                count = sorted.size.toLong(),
                minMs = sorted.firstOrNull() ?: 0,
                maxMs = sorted.lastOrNull() ?: 0,
                avgMs = if (sorted.isNotEmpty()) sorted.average().toLong() else 0,
                p50Ms = percentile(sorted, 50),
                p95Ms = percentile(sorted, 95),
                p99Ms = percentile(sorted, 99),
            )
        }.sortedBy { it.name }

        val connSorted = synchronized(connectionTimes) { connectionTimes.sorted() }

        return StressReport(
            durationMs = elapsed,
            totalActions = actions,
            totalErrors = errors,
            throughput = if (elapsed > 0) actions.toDouble() / (elapsed / 1000.0) else 0.0,
            errorRate = if (actions > 0) errors.toDouble() / actions else 0.0,
            connectionTimeAvgMs = if (connSorted.isNotEmpty()) connSorted.average().toLong() else 0,
            connectionTimeP95Ms = percentile(connSorted, 95),
            actionStats = actionStats,
        )
    }

    private fun percentile(sorted: List<Long>, p: Int): Long {
        if (sorted.isEmpty()) return 0
        val index = ((p / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }
}

data class StressReport(
    val durationMs: Long,
    val totalActions: Long,
    val totalErrors: Long,
    val throughput: Double,
    val errorRate: Double,
    val connectionTimeAvgMs: Long,
    val connectionTimeP95Ms: Long,
    val actionStats: List<ActionStats>,
) {
    fun printReport() {
        println()
        println("=" .repeat(70))
        println("  STRESS TEST REPORT")
        println("=".repeat(70))
        println()
        println("  Duration:           ${durationMs / 1000.0}s")
        println("  Total Actions:      $totalActions")
        println("  Total Errors:       $totalErrors")
        println("  Throughput:         ${"%.2f".format(throughput)} actions/sec")
        println("  Error Rate:         ${"%.2f".format(errorRate * 100)}%")
        println("  Connection Avg:     ${connectionTimeAvgMs}ms")
        println("  Connection P95:     ${connectionTimeP95Ms}ms")
        println()
        println("-".repeat(70))
        println("  %-20s %6s %6s %6s %6s %6s %6s %6s".format(
            "Action", "Count", "Min", "Avg", "P50", "P95", "P99", "Max"
        ))
        println("-".repeat(70))
        for (stat in actionStats) {
            println("  %-20s %6d %5dms %5dms %5dms %5dms %5dms %5dms".format(
                stat.name, stat.count, stat.minMs, stat.avgMs,
                stat.p50Ms, stat.p95Ms, stat.p99Ms, stat.maxMs
            ))
        }
        println("-".repeat(70))
        println()
    }
}

data class ActionStats(
    val name: String,
    val count: Long,
    val minMs: Long,
    val maxMs: Long,
    val avgMs: Long,
    val p50Ms: Long,
    val p95Ms: Long,
    val p99Ms: Long,
)
