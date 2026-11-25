package br.com.wdc.store.test.security

import br.com.wdc.store.domain.security.AuthResult
import br.com.wdc.store.domain.security.AuthenticationService
import br.com.wdc.store.domain.security.JceCryptoProvider
import br.com.wdc.store.domain.security.Scram
import br.com.wdc.store.domain.user.UserRepository
import br.com.wdc.store.persistence.security.AuthenticationServiceImpl
import br.com.wdc.store.persistence.security.PostgresSessionEvents
import br.com.wdc.store.persistence.security.SessionEvents
import br.com.wdc.store.test.util.RestTestEnvironment
import br.com.wdc.store.test.util.TestDatabase
import br.com.wdc.store.test.util.TestEnvironmentExtension
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * Two backend instances on the same database: a session ended on one stops being accepted by the other, which had
 * already read it into memory. The environment's service is instance A; the test makes instance B.
 */
class SessionEventsTest {

    companion object {
        private const val SECRET = "session-events-secret-0123456789-012345"

        private val env = RestTestEnvironment("wedocode-store-session-events", jwtSecret = SECRET)

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private suspend fun signIn(service: AuthenticationService): AuthResult {
        val challenge = service.challenge("fulano")
        val proof = Scram(JceCryptoProvider()).respond("fulano", "fulano", challenge).proof
        return service.login(
            userName = "fulano",
            proof = proof,
            nonce = challenge.nonce,
        ) ?: fail("fulano could not sign in")
    }

    @Test
    fun aSessionEndedOnOneInstance_isRefusedByAnother_soon(): Unit = runBlocking {
        assumeTrue(TestDatabase.postgres, "LISTEN/NOTIFY: PostgreSQL only")
        val instanceA = AuthenticationService.BEAN.get()
        PostgresSessionEvents(env.dataSource).start().use { events ->
            val instanceB = AuthenticationServiceImpl(
                rawUserRepo = UserRepository.BEAN.get(),
                jwtSecret = SECRET,
                sessionEvents = events,
            )
            delay(500) // B's listener is on

            val session = signIn(instanceA)
            assertNotNull(instanceB.resolveToken(session.accessToken), "B reads the session, and keeps it")

            instanceA.logout(session.refreshToken)

            repeat(50) {
                if (instanceB.resolveToken(session.accessToken) == null) return@runBlocking
                delay(100)
            }
            fail("B still accepts a session A ended, 5 seconds later")
        }
    }

    @Test
    fun withoutEvents_anotherInstanceKeepsAcceptingIt(): Unit = runBlocking {
        // why the events exist: with none, B keeps the session in memory after A ended it
        val instanceA = AuthenticationService.BEAN.get()
        val instanceB = AuthenticationServiceImpl(
            rawUserRepo = UserRepository.BEAN.get(),
            jwtSecret = SECRET,
            sessionEvents = SessionEvents.LOCAL,
        )
        val session = signIn(instanceA)
        assertNotNull(instanceB.resolveToken(session.accessToken))

        instanceA.logout(session.refreshToken)
        delay(1_000)

        assertNotNull(instanceB.resolveToken(session.accessToken), "kept from memory: the gap the events close")
    }
}
