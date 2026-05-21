package br.com.wdc.shopping.view.react.stress

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory

/**
 * Collects server-side metrics from a dedicated endpoint on the backend.
 * The endpoint `/api/stress/metrics` must be enabled in the backend.
 */
class ServerMetricsClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient,
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(ServerMetricsClient::class.java)
        private val GSON: Gson = GsonBuilder().create()
        private val MAP_TYPE = object : TypeToken<Map<String, Any?>>() {}
    }

    /**
     * Fetches the current server metrics snapshot.
     * Returns null if the endpoint is unavailable.
     */
    fun fetch(): ServerMetrics? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/stress/metrics")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val body = response.body?.string() ?: return null
            response.close()

            val map: Map<String, Any?> = GSON.fromJson(body, MAP_TYPE.type)
            ServerMetrics(
                activeSessions = (map["activeSessions"] as? Number)?.toInt() ?: 0,
                heapUsedMb = (map["heapUsedMb"] as? Number)?.toLong() ?: 0,
                heapMaxMb = (map["heapMaxMb"] as? Number)?.toLong() ?: 0,
                threadCount = (map["threadCount"] as? Number)?.toInt() ?: 0,
                totalRequests = (map["totalRequests"] as? Number)?.toLong() ?: 0,
                avgResponseMs = (map["avgResponseMs"] as? Number)?.toDouble() ?: 0.0,
            )
        } catch (e: Exception) {
            LOG.debug("Failed to fetch server metrics: {}", e.message)
            null
        }
    }
}

data class ServerMetrics(
    val activeSessions: Int,
    val heapUsedMb: Long,
    val heapMaxMb: Long,
    val threadCount: Int,
    val totalRequests: Long,
    val avgResponseMs: Double,
) {
    fun print() {
        println("  [Server] Sessions: $activeSessions | Heap: ${heapUsedMb}/${heapMaxMb}MB | Threads: $threadCount | Requests: $totalRequests | AvgResp: ${"%.1f".format(avgResponseMs)}ms")
    }
}
