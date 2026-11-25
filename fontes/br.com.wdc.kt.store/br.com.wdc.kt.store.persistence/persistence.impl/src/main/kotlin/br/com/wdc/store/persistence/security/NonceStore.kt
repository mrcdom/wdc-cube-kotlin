package br.com.wdc.store.persistence.security

import br.com.wdc.store.persistence.StoreDSLContext
import br.com.wdc.store.persistence.scheme.tables.references.EN_AUTH_NONCE
import org.jooq.DSLContext
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64

/**
 * The sign-in challenges in flight: each nonce is random, belongs to the user it was made for, is good for one
 * attempt ([consume] removes it) and expires in [NONCE_TTL].
 *
 * They are kept in the database (`EN_AUTH_NONCE`, `EXPIRES_AT` in UTC), not in one instance's memory: with several
 * backend instances, the challenge and the login may reach different ones. Using a nonce up is one `DELETE` that
 * only removes it if it is the user's and has not expired, so it works once, whichever instance asks first.
 */
class NonceStore(private val clock: Clock = Clock.systemUTC()) {

    companion object {
        private val NONCE_TTL = Duration.ofSeconds(60)
        private val RANDOM = SecureRandom()
    }

    private fun dsl(): DSLContext = StoreDSLContext.BEAN.get()

    private fun utc(instant: Instant): LocalDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC)

    /** A new nonce for [userName]. */
    fun generate(userName: String): String {
        val bytes = ByteArray(24).also(RANDOM::nextBytes)
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        dsl().insertInto(EN_AUTH_NONCE)
            .set(EN_AUTH_NONCE.NONCE, nonce)
            .set(EN_AUTH_NONCE.USERNAME, userName)
            .set(EN_AUTH_NONCE.EXPIRES_AT, utc(Instant.now(clock).plus(NONCE_TTL)))
            .execute()
        return nonce
    }

    fun expiresAt(nonce: String): Instant? =
        dsl().select(EN_AUTH_NONCE.EXPIRES_AT).from(EN_AUTH_NONCE).where(EN_AUTH_NONCE.NONCE.eq(nonce))
            .fetchOne(EN_AUTH_NONCE.EXPIRES_AT)
            ?.toInstant(ZoneOffset.UTC)

    /** Uses up [nonce]: `true` when it exists, has not expired and was made for [userName]. Either way it is gone. */
    fun consume(nonce: String, userName: String): Boolean {
        val used = dsl().deleteFrom(EN_AUTH_NONCE)
            .where(EN_AUTH_NONCE.NONCE.eq(nonce))
            .and(EN_AUTH_NONCE.USERNAME.eq(userName))
            .and(EN_AUTH_NONCE.EXPIRES_AT.gt(utc(Instant.now(clock))))
            .execute() == 1
        // one attempt: a nonce tried for another user, or too late, does not stay
        if (!used) dsl().deleteFrom(EN_AUTH_NONCE).where(EN_AUTH_NONCE.NONCE.eq(nonce)).execute()
        return used
    }

    fun evictExpired() {
        dsl().deleteFrom(EN_AUTH_NONCE).where(EN_AUTH_NONCE.EXPIRES_AT.lt(utc(Instant.now(clock)))).execute()
    }
}
