package br.com.wdc.store.persistence.security

import br.com.wdc.framework.domain.exception.TooManyRequestsException
import br.com.wdc.store.persistence.StoreDSLContext
import br.com.wdc.store.persistence.scheme.tables.references.EN_LOGIN_FAILURE
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Slows down password guessing on one account, whatever the way in (the REST API or a view whose presenters run on
 * the server). After [freeFailures] failed sign-ins in a row, each new attempt waits — [firstWait], doubling with
 * each failure, up to [maxWait]; a success clears the count. A wait, not a lock: the user signs in once it passes,
 * and no one can lock an account out for good by failing on purpose. Failures older than [forgetAfter] are forgotten.
 *
 * Kept in the database (`EN_LOGIN_FAILURE`: the count and the last failure, in UTC), not in one instance's memory:
 * with several backend instances, attempts spread over them count as one. The wait is worked out from those two when
 * an attempt is checked, so the only write instances can race on is the count, and that is one `UPDATE` that adds
 * one. It complements the per-address limit of `/api/auth`, which can't tell a crowd of addresses aiming at one
 * account.
 */
class LoginThrottle(
    private val freeFailures: Int = 5,
    private val firstWait: Duration = Duration.ofSeconds(30),
    private val maxWait: Duration = Duration.ofMinutes(15),
    private val forgetAfter: Duration = Duration.ofHours(1),
    private val clock: Clock = Clock.systemUTC(),
) {

    private companion object {
        /** The width of `USERNAME`: a longer name is kept by its hash. */
        const val MAX_KEY = 100
    }

    private fun dsl(): DSLContext = StoreDSLContext.BEAN.get()

    private fun utc(instant: Instant): LocalDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC)

    /** Refuses the attempt while [userName] must wait: [TooManyRequestsException] with the seconds left. */
    fun check(userName: String) {
        val row = dsl().select(EN_LOGIN_FAILURE.FAILURES, EN_LOGIN_FAILURE.LAST_FAILURE)
            .from(EN_LOGIN_FAILURE)
            .where(EN_LOGIN_FAILURE.USERNAME.eq(key(userName)))
            .fetchOne() ?: return
        val last = row.value2()!!.toInstant(ZoneOffset.UTC)
        val now = Instant.now(clock)
        if (last.plus(forgetAfter).isBefore(now)) return
        val wait = waitAfter(row.value1()!!) ?: return
        val left = Duration.between(now, last.plus(wait))
        if (!left.isNegative && !left.isZero) {
            throw TooManyRequestsException("Too many failed sign-ins: try again later", (left.toMillis() + 999) / 1000)
        }
    }

    fun failed(userName: String) {
        val key = key(userName)
        val now = utc(Instant.now(clock))
        val cutoff = utc(Instant.now(clock).minus(forgetAfter))
        // add one (or start over, past forgetAfter); the first failure inserts, and one inserted meanwhile by another
        // instance is counted on the next round
        repeat(3) {
            val updated = dsl().update(EN_LOGIN_FAILURE)
                .set(
                    EN_LOGIN_FAILURE.FAILURES,
                    DSL.`when`(EN_LOGIN_FAILURE.LAST_FAILURE.lt(cutoff), DSL.inline(1))
                        .otherwise(EN_LOGIN_FAILURE.FAILURES.plus(1)),
                )
                .set(EN_LOGIN_FAILURE.LAST_FAILURE, now)
                .where(EN_LOGIN_FAILURE.USERNAME.eq(key))
                .execute()
            if (updated == 1) return
            try {
                dsl().insertInto(EN_LOGIN_FAILURE)
                    .set(EN_LOGIN_FAILURE.USERNAME, key)
                    .set(EN_LOGIN_FAILURE.FAILURES, 1)
                    .set(EN_LOGIN_FAILURE.LAST_FAILURE, now)
                    .execute()
                return
            } catch (_: DataAccessException) {
                // another instance inserted it first: update it
            }
        }
    }

    fun succeeded(userName: String) {
        dsl().deleteFrom(EN_LOGIN_FAILURE).where(EN_LOGIN_FAILURE.USERNAME.eq(key(userName))).execute()
    }

    fun evictExpired() {
        val cutoff = utc(Instant.now(clock).minus(forgetAfter))
        dsl().deleteFrom(EN_LOGIN_FAILURE).where(EN_LOGIN_FAILURE.LAST_FAILURE.lt(cutoff)).execute()
    }

    /** The wait after [failures] in a row: none while they are free. */
    private fun waitAfter(failures: Int): Duration? {
        val over = failures - freeFailures
        return if (over <= 0) null else minOf(maxWait, firstWait.multipliedBy(1L shl minOf(over - 1, 20)))
    }

    private fun key(userName: String): String {
        val key = userName.trim().lowercase()
        if (key.length <= MAX_KEY) return key
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return "#" + digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
