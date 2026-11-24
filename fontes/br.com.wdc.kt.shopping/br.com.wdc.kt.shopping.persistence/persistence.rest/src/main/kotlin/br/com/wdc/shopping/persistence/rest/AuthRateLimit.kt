package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.rest.security.RateLimitedException
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Limits the `/api/auth` requests (challenge, login, refresh) per client address and minute: they are open — no
 * token yet —, so the framework's per-principal limit doesn't reach them, and a password guessed by brute force goes
 * through here. Fixed window per clock minute; above [perMinute], [RateLimitedException] (429 with `Retry-After`).
 * The count is per instance, in memory. The address is the connection's: behind a proxy, every client is the proxy's.
 */
internal class AuthRateLimit(private val perMinute: Int, private val clock: Clock = Clock.systemUTC()) {

    private class Window(val minute: Long) {
        val count = AtomicLong()
    }

    private val windows = ConcurrentHashMap<String, Window>()
    private val cleaned = AtomicLong(Long.MIN_VALUE)

    /** Counts a request from [address]; above the limit, [RateLimitedException]. */
    fun check(address: String) {
        if (perMinute <= 0) return
        val now = clock.millis()
        val minute = Math.floorDiv(now, MINUTE)
        val last = cleaned.get()
        if (last != minute && cleaned.compareAndSet(last, minute)) {
            windows.entries.removeIf { it.value.minute < minute }
        }
        val window = windows.compute(address) { _, current ->
            if (current == null || current.minute != minute) Window(minute) else current
        }!!
        if (window.count.incrementAndGet() > perMinute) {
            val remaining = MINUTE - Math.floorMod(now, MINUTE)
            throw RateLimitedException((remaining + 999) / 1000, "Too many authentication requests")
        }
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
