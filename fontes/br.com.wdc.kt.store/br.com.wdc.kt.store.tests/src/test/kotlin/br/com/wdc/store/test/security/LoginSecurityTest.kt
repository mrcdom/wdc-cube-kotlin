package br.com.wdc.store.test.security

import br.com.wdc.framework.domain.exception.TooManyRequestsException
import br.com.wdc.framework.domain.exception.UnauthenticatedException
import br.com.wdc.store.domain.security.AuthenticationService
import br.com.wdc.store.domain.security.ChallengeResult
import br.com.wdc.store.domain.security.CryptoProvider
import br.com.wdc.store.domain.security.JceCryptoProvider
import br.com.wdc.store.domain.security.Scram
import br.com.wdc.store.domain.security.ScramVerifier
import br.com.wdc.store.domain.user.UserRepository
import br.com.wdc.store.persistence.client.RestAuthClient
import br.com.wdc.store.persistence.security.AuthenticationServiceImpl
import br.com.wdc.store.persistence.security.LoginThrottle
import br.com.wdc.store.presentation.presenter.open.login.calls.CredentialsCheck
import br.com.wdc.store.test.util.RestTestEnvironment
import br.com.wdc.store.test.util.TestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A clock the test moves. */
private class MovableClock(var now: Instant = Instant.parse("2026-11-25T10:00:00Z")) : Clock() {
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId) = this
    override fun instant() = now
}

/** Failed sign-ins on one account wait longer and longer; a success, or an hour, clears them. */
class LoginThrottleTest {

    private val clock = MovableClock()
    private val throttle = LoginThrottle(clock = clock)

    @Test
    fun fiveFailuresAreFree_thenEachOneWaits_doublingTheWait() {
        repeat(5) { throttle.check("fulano"); throttle.failed("fulano") }
        throttle.check("fulano")

        throttle.failed("fulano")
        assertEquals(30, assertFailsWith<TooManyRequestsException> { throttle.check("fulano") }.retryAfterSeconds)
        assertFailsWith<TooManyRequestsException> { throttle.check("FULANO ") }

        clock.now = clock.now.plusSeconds(31)
        throttle.check("fulano")
        throttle.failed("fulano")
        assertEquals(60, assertFailsWith<TooManyRequestsException> { throttle.check("fulano") }.retryAfterSeconds)

        throttle.check("beotrano")
    }

    @Test
    fun aSuccess_clearsTheCount_andAnHourForgetsIt() {
        repeat(6) { throttle.failed("fulano") }
        throttle.succeeded("fulano")
        throttle.check("fulano")

        repeat(5) { throttle.failed("beotrano") }
        clock.now = clock.now.plus(Duration.ofMinutes(61))
        throttle.failed("beotrano")
        throttle.check("beotrano")
    }

    @Test
    fun theWait_hasACeiling() {
        repeat(30) { throttle.failed("fulano") }
        assertEquals(15 * 60, assertFailsWith<TooManyRequestsException> { throttle.check("fulano") }.retryAfterSeconds)
    }
}

/** Signing in through `/api/auth` with SCRAM-SHA-256: what the exchange proves, and what it does not tell. */
class ScramLoginTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-store-scram-login", jwtSecret = "scram-login-secret-0123456789-0123")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private fun client() = RestAuthClient(env.restConfig)

    @Test
    fun theUserSignsIn_andTheServerProvesItHoldsTheVerifier() {
        val result = assertNotNull(client().login("fulano", "fulano"))
        assertTrue(result.serverSignature.isNotEmpty())
    }

    @Test
    fun aWrongPassword_isRefused() {
        assertNull(client().login("fulano", "not-the-password"))
    }

    @Test
    fun anUnknownUser_getsAChallengeLikeAKnownOne() {
        val ghost = client().challenge("ghost")
        assertEquals(ghost.salt, client().challenge("ghost").salt, "the same made-up salt every time")
        assertEquals(ScramVerifier.DEFAULT_ITERATIONS, ghost.iterations)
        assertNotEquals(client().challenge("fulano").salt, ghost.salt)

        assertNull(client().login("ghost", "anything"), "refused like a wrong password")
    }

    @Test
    fun aNonce_isForTheUserItWasMadeFor_andForOneAttempt() {
        val auth = client()
        val fulano = auth.challenge("fulano")
        val beotranosNonce = auth.challenge("beotrano").nonce
        val scram = Scram(JceCryptoProvider())

        // fulano's right proof, over a nonce made for someone else
        val stolen = scram.respond("fulano", "fulano", ChallengeResult(beotranosNonce, fulano.salt, fulano.iterations, fulano.expiresAt))
        assertNull(auth.login("fulano", stolen.proof, beotranosNonce))

        // the right proof over the right nonce: once
        val proof = scram.respond("fulano", "fulano", fulano).proof
        assertNotNull(auth.login("fulano", proof, fulano.nonce))
        assertNull(auth.login("fulano", proof, fulano.nonce), "a nonce is used up")
    }

    @Test
    fun manyFailuresOnOneUser_makeItWait_evenWithTheRightPassword() {
        repeat(6) { assertNull(client().login("beotrano", "wrong-$it")) }
        val e = assertFailsWith<TooManyRequestsException> { client().login("beotrano", "beotrano") }
        assertTrue(e.message!!.startsWith("HTTP 429"), e.message)
    }

    @Test
    fun theRefusalOfAWrongPassword_isTyped() {
        assertFailsWith<UnauthenticatedException> { env.loginAs("fulano", "not-the-password") }
    }
}

/**
 * The same limit when the presenters run on the server (the remote React view): the login does not go through
 * `/api/auth`, it calls the service in the same process.
 */
class InProcessLoginThrottleTest {

    companion object {
        private val env = TestEnvironment("wedocode-store-in-process-login")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    @Test
    fun credentialsCheck_inTheServersProcess_isThrottledToo(): Unit = runBlocking {
        CryptoProvider.BEAN.set(JceCryptoProvider())
        val previous = AuthenticationService.BEAN.getOrNull()
        AuthenticationService.BEAN.set(AuthenticationServiceImpl(UserRepository.BEAN.get(), "in-process-secret-0123456789-0123"))
        try {
            assertNotNull(CredentialsCheck(userName = "fulano", password = "fulano").run())
            repeat(6) { assertNull(CredentialsCheck(userName = "fulano", password = "wrong-$it").run()) }
            val e = assertFailsWith<TooManyRequestsException> { CredentialsCheck(userName = "fulano", password = "fulano").run() }
            assertNotNull(e.retryAfterSeconds)
        } finally {
            AuthenticationService.BEAN.set(previous)
        }
    }
}
