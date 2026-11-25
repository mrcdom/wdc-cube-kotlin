package br.com.wdc.store.persistence.security

import br.com.wdc.framework.domain.exception.TooManyRequestsException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Slows down password guessing on one account, whatever the way in (the REST API or a view whose presenters run on
 * the server). After [freeFailures] failed sign-ins in a row, each new attempt waits — [firstWait], doubling with
 * each failure, up to [maxWait]; a success clears the count. A wait, not a lock: the user signs in once it passes,
 * and no one can lock an account out for good by failing on purpose. Failures older than [forgetAfter] are forgotten.
 *
 * Kept in memory, per server instance. It complements the per-address limit of `/api/auth`, which can't tell a
 * crowd of addresses aiming at one account.
 */
class LoginThrottle(
    private val freeFailures: Int = 5,
    private val firstWait: Duration = Duration.ofSeconds(30),
    private val maxWait: Duration = Duration.ofMinutes(15),
    private val forgetAfter: Duration = Duration.ofHours(1),
    private val clock: Clock = Clock.systemUTC(),
) {

    private class Failures(val count: Int, val last: Instant, val waitUntil: Instant?)

    private val byUser = ConcurrentHashMap<String, Failures>()

    /** Refuses the attempt while [userName] must wait: [TooManyRequestsException] with the seconds left. */
    fun check(userName: String) {
        val waitUntil = byUser[key(userName)]?.waitUntil ?: return
        val left = Duration.between(Instant.now(clock), waitUntil)
        if (!left.isNegative && !left.isZero) {
            throw TooManyRequestsException("Too many failed sign-ins: try again later", (left.toMillis() + 999) / 1000)
        }
    }

    fun failed(userName: String) {
        val now = Instant.now(clock)
        byUser.compute(key(userName)) { _, previous ->
            val count = if (previous == null || previous.last.plus(forgetAfter).isBefore(now)) 1 else previous.count + 1
            val over = count - freeFailures
            val wait = if (over <= 0) null else minOf(maxWait, firstWait.multipliedBy(1L shl minOf(over - 1, 20)))
            Failures(count, now, wait?.let { now.plus(it) })
        }
    }

    fun succeeded(userName: String) {
        byUser.remove(key(userName))
    }

    fun evictExpired() {
        val now = Instant.now(clock)
        byUser.entries.removeIf { it.value.last.plus(forgetAfter).isBefore(now) }
    }

    private fun key(userName: String) = userName.trim().lowercase()
}
