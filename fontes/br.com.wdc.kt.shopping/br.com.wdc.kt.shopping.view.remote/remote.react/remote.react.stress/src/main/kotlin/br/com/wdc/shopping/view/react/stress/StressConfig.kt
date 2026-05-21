package br.com.wdc.shopping.view.react.stress

/**
 * Configuration for the stress test runner.
 */
data class StressConfig(
    /** Base URL of the server (e.g., "http://localhost:8080") */
    val baseUrl: String = "http://localhost:8080",

    /** Number of virtual clients to spawn */
    val clientCount: Int = 10,

    /** Ramp-up duration in milliseconds (time to spawn all clients) */
    val rampUpMs: Long = 5_000,

    /** Duration of the stress test in milliseconds */
    val durationMs: Long = 60_000,

    /** Think time between actions in milliseconds (simulates user pauses) */
    val thinkTimeMs: Long = 500,

    /** Random variation on think time (0.0 = fixed, 1.0 = 0..2x thinkTime) */
    val thinkTimeJitter: Double = 0.5,

    /** Warmup duration in milliseconds (metrics not counted) */
    val warmupMs: Long = 5_000,

    /** Login credentials to use (cycled across clients) */
    val users: List<UserCredentials> = listOf(
        UserCredentials("admin", "admin")
    ),

    /** Context path for the remote.react app (default uses root) */
    val contextPath: String = "",

    /** Whether to collect server-side metrics via endpoint */
    val collectServerMetrics: Boolean = true,

    /** Server metrics polling interval in milliseconds */
    val serverMetricsPollMs: Long = 2_000,
)

data class UserCredentials(
    val userName: String,
    val password: String,
)
