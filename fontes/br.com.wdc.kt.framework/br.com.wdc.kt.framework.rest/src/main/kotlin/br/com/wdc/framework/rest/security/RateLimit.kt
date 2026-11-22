package br.com.wdc.framework.rest.security

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-minute request limit of each caller ([SecurityConfig.rateLimit]). Evaluated after authentication and before
 * the access rule; a request without a principal (a public route called without a credential) doesn't count.
 *
 * ```kotlin
 * security {
 *     authenticator = …
 *     rateLimit {
 *         key = { p -> p.id }
 *         perMinute = { p -> (p as Partner).requestsPerMinute }   // null = no limit
 *     }
 * }
 * ```
 *
 * The window is fixed, per clock minute. Above the limit, [RateLimitedException]: by the default mapping, 429 with
 * `Retry-After` (the seconds until the minute turns); the application's `ExceptionMapping` can change the response.
 *
 * **The count is in memory, per instance:** with N instances behind a load balancer, the effective limit is up to N
 * times the configured one.
 */
class RateLimitOptions {

    /** The count key. Default: the principal's id. */
    var key: (RestPrincipal) -> String = { it.id }

    /** The caller's per-minute limit. `null` = no limit. Default: no limit. */
    var perMinute: (RestPrincipal) -> Int? = { null }

    /** The windows' clock. Replace it for tests. */
    var clock: Clock = Clock.systemUTC()
}

/** The caller exceeded the per-minute limit. 429 with `Retry-After` by the default mapping. */
open class RateLimitedException(
    /** The seconds until the next window, rounded up. */
    val retryAfterSeconds: Long,
    message: String = "Too many requests",
) : RuntimeException(message)

/** The count: fixed per-minute window, per key. */
internal class RateLimiter(private val options: RateLimitOptions) {

    private class Window(val minute: Long) {
        val count = AtomicLong()
    }

    private val windows = ConcurrentHashMap<String, Window>()

    /** The minute of the last cleanup; older windows are removed on the first count of a new minute. */
    private val cleaned = AtomicLong(Long.MIN_VALUE)

    /** Counts [principal]'s request; above the limit, [RateLimitedException]. */
    fun check(principal: RestPrincipal) {
        val limit = options.perMinute(principal) ?: return
        val now = options.clock.millis()
        val minute = Math.floorDiv(now, MINUTE)
        cleanUp(minute)
        val window = windows.compute(options.key(principal)) { _, current ->
            if (current == null || current.minute != minute) Window(minute) else current
        }!!
        if (window.count.incrementAndGet() > limit) {
            val remaining = MINUTE - Math.floorMod(now, MINUTE)
            throw RateLimitedException((remaining + 999) / 1000)
        }
    }

    internal val size: Int get() = windows.size

    private fun cleanUp(minute: Long) {
        val last = cleaned.get()
        if (last == minute || !cleaned.compareAndSet(last, minute)) return
        windows.entries.removeIf { it.value.minute < minute }
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
