package br.com.wdc.shopping.view.react.stress

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Orchestrates the stress test: spawns virtual clients, executes scenarios,
 * manages ramp-up, and produces the final report.
 */
class StressRunner(private val config: StressConfig) {

    companion object {
        private val LOG = LoggerFactory.getLogger(StressRunner::class.java)
    }

    private val metrics = MetricsCollector()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(okhttp3.CookieJar.NO_COOKIES) // We manage cookies manually
        .build()

    fun run(): StressReport {
        LOG.info("=== Stress Test Starting ===")
        LOG.info("  Server:    {}", config.baseUrl)
        LOG.info("  Clients:   {}", config.clientCount)
        LOG.info("  Duration:  {}s", config.durationMs / 1000)
        LOG.info("  Ramp-up:   {}s", config.rampUpMs / 1000)
        LOG.info("  Warmup:    {}s", config.warmupMs / 1000)
        LOG.info("  ThinkTime: {}ms (jitter: {})", config.thinkTimeMs, config.thinkTimeJitter)
        LOG.info("")

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        try {
            runBlocking {
                // Phase 1: Ramp-up — spawn clients gradually
                val clients = mutableListOf<Job>()
                val delayBetweenClients = config.rampUpMs / config.clientCount

                metrics.markStart()

                for (i in 0 until config.clientCount) {
                    val credentials = config.users[i % config.users.size]
                    val job = scope.launch {
                        runClient(i, credentials)
                    }
                    clients.add(job)

                    if (i < config.clientCount - 1) {
                        delay(delayBetweenClients)
                    }
                }

                LOG.info("All {} clients launched. Running for {}s...", config.clientCount, config.durationMs / 1000)

                // Phase 2: Wait for warmup to complete
                delay(config.warmupMs)
                metrics.warmupComplete = true
                LOG.info("Warmup complete. Collecting metrics...")

                // Phase 3: Run for the configured duration
                delay(config.durationMs)

                // Phase 4: Stop
                LOG.info("Duration reached. Stopping clients...")
                scope.cancel()

                // Give clients time to disconnect gracefully
                delay(2_000)
            }
        } catch (e: Exception) {
            LOG.error("Stress test failed", e)
        } finally {
            httpClient.dispatcher.executorService.shutdown()
            httpClient.connectionPool.evictAll()
        }

        metrics.markEnd()
        return metrics.generateReport()
    }

    private suspend fun runClient(clientId: Int, credentials: UserCredentials) {
        val client = VirtualClient(clientId, config, credentials, metrics, httpClient)

        try {
            if (!client.connect()) {
                LOG.error("[Client-{}] Initial connection failed, stopping", clientId)
                return
            }

            val scenarios = listOf(
                FullShoppingScenario(credentials),
                BrowseOnlyScenario(credentials),
                RapidNavigationScenario(credentials),
            )

            // Repeatedly execute random scenarios until cancelled
            while (currentCoroutineContext().job.isActive) {
                val scenario = scenarios[Random.nextInt(scenarios.size)]
                LOG.debug("[Client-{}] Executing scenario: {}", clientId, scenario.name)

                scenario.execute(client, metrics)

                // Think time between scenarios
                val thinkTime = computeThinkTime()
                delay(thinkTime)

                // Reconnect if disconnected
                if (!client.isConnected()) {
                    LOG.debug("[Client-{}] Reconnecting...", clientId)
                    if (!client.connect()) {
                        LOG.warn("[Client-{}] Reconnection failed, stopping", clientId)
                        return
                    }
                }
            }
        } catch (e: CancellationException) {
            // Normal shutdown
        } catch (e: Exception) {
            LOG.error("[Client-{}] Unexpected error: {}", clientId, e.message, e)
        } finally {
            client.disconnect()
        }
    }

    private fun computeThinkTime(): Long {
        val base = config.thinkTimeMs
        val jitter = config.thinkTimeJitter
        if (jitter <= 0.0) return base
        val variation = (Random.nextDouble() * 2.0 - 1.0) * jitter * base
        return (base + variation).toLong().coerceAtLeast(0)
    }
}
