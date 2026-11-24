package br.com.wdc.shopping.test.security

import br.com.wdc.shopping.domain.ShoppingConfig
import br.com.wdc.shopping.persistence.ShoppingDSLContext
import br.com.wdc.shopping.persistence.scheme.tables.references.EN_USER_SESSION
import br.com.wdc.shopping.persistence.security.AccessContextCache
import br.com.wdc.shopping.persistence.security.IntentSecretStore
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** Sessions on the server: the refresh token's window and the private key stored encrypted. */
class SessionSecurityTest {

    companion object {
        private val env = TestEnvironment("wedocode-shopping-session-security")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)

        private const val SECRET = "session-security-test-secret-0123456789"
    }

    /** A clock the test moves. */
    private class MovableClock(var now: Instant = Instant.parse("2026-11-24T10:00:00Z")) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant() = now
    }

    private fun cache(clock: Clock = Clock.systemUTC(), secret: String = SECRET) =
        AccessContextCache(secret, IntentSecretStore(), refreshTokenTtlDays = 7, clock = clock)

    private fun storedPrivateKey(sessionId: String): String? =
        ShoppingDSLContext.BEAN.get().select(EN_USER_SESSION.RSA_PRIVATE_KEY).from(EN_USER_SESSION)
            .where(EN_USER_SESSION.SESSION_ID.eq(sessionId)).fetchOne()?.value1()

    @Test
    fun aRefreshToken_worksInsideItsWindow_andNotAfterIt() {
        val clock = MovableClock()
        val cache = cache(clock)
        val first = cache.createSession(DBReset.FULANO_ID, "fulano", setOf("CUSTOMER"))

        clock.now = clock.now.plus(Duration.ofDays(3))
        val second = cache.refresh(first.refreshToken)
        assertNotNull(second, "three days later, still inside the window")

        // the window counts from the end of the access token the refresh token came with: 30 min + 7 days
        clock.now = clock.now.plus(Duration.ofDays(7)).plus(Duration.ofMinutes(31))
        assertNull(cache.refresh(second!!.refreshToken), "past its window, the refresh token is refused")
        assertNull(storedPrivateKey(second.sessionId), "and its session is gone")
    }

    @Test
    fun thePrivateKey_isStoredEncrypted_andOnlyThisSecretRestoresTheSession() {
        val session = cache().createSession(DBReset.FULANO_ID, "fulano", setOf("CUSTOMER"))

        val stored = storedPrivateKey(session.sessionId)!!
        assertTrue(stored.startsWith("v1:"), "encrypted, not the PKCS#8 key in Base64")

        // another server instance (empty memory) restores it from the database
        val restored = cache().getBySessionId(session.sessionId)
        assertEquals(session.rsaKeyPair.private.encoded.toList(), restored!!.rsaKeyPair.private.encoded.toList())

        // with another secret, the key doesn't decrypt: the session is not restored
        assertNull(cache(secret = "another-secret-0123456789-0123456789").getBySessionId(session.sessionId))
    }

    @Test
    fun evictExpired_dropsTheSessionsPastTheirWindow_fromTheDatabase() {
        val clock = MovableClock()
        val cache = cache(clock)
        val session = cache.createSession(DBReset.FULANO_ID, "fulano", setOf("CUSTOMER"))

        clock.now = clock.now.plus(Duration.ofDays(8))
        cache.evictExpired()

        assertNull(storedPrivateKey(session.sessionId))
    }
}

/** `/api/auth` is open (no token yet), so it has a limit of its own, per client address and minute. */
class AuthRateLimitTest {

    companion object {
        init {
            ShoppingConfig.Internals.setAuthRequestsPerMinute(3)
        }

        private val env = RestTestEnvironment("wedocode-shopping-auth-rate-limit", jwtSecret = "auth-rate-limit-secret-0123456789")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)

        @JvmStatic
        @AfterAll
        fun restore() {
            ShoppingConfig.Internals.setAuthRequestsPerMinute(60)
        }
    }

    @Test
    fun aboveTheLimit_theChallengeAnswers429_withRetryAfter() {
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${env.port}/api/auth/challenge")).GET().build()
        // the count is per clock minute: too close to its end, wait for the next one
        val intoTheMinute = System.currentTimeMillis() % 60_000
        if (intoTheMinute > 50_000) Thread.sleep(60_000 - intoTheMinute + 100)
        val statuses = (1..4).map { client.send(request, HttpResponse.BodyHandlers.ofString()) }
        assertEquals(listOf(200, 200, 200, 429), statuses.map { it.statusCode() })
        assertTrue(statuses.last().headers().firstValue("Retry-After").isPresent)
    }
}
