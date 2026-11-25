package br.com.wdc.store.test.repository

import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.store.domain.security.SessionListener
import br.com.wdc.store.persistence.client.BaseHttpTransport
import br.com.wdc.store.persistence.client.RestAuthenticationService
import br.com.wdc.store.persistence.client.RestConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The client's session when several calls are refused at once: HTTP no longer blocks, so the calls of two actions
 * can be on the way together, both with the expired token. The server replaces the refresh token on each renewal; a
 * second renewal with the old one would be refused and lose the session.
 */
class SessionRenewalTest {

    /** A server that refuses the old access token, and renews the session once per refresh token. */
    private class FakeServer(private val callsAtOnce: Int) : BaseHttpTransport("http://fake") {
        var renewals = 0
        private var refreshToken = "r1"
        private var accessToken = "not-issued-yet" // "old" has expired
        private var waiting = 0
        private val allSent = CompletableDeferred<Unit>()

        override suspend fun exchange(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Answer {
            if (url.endsWith("/api/auth/refresh")) {
                yield() // lets the other refused calls ask too
                val sent = body!!.decodeToString()
                if ("\"$refreshToken\"" !in sent) return Answer(401, """{"error":"Invalid or expired refresh token"}""".encodeToByteArray())
                renewals++
                refreshToken = "r${renewals + 1}"
                accessToken = "new$renewals"
                return Answer(200, """{"userId":1,"accessToken":"$accessToken","refreshToken":"$refreshToken","expiresAt":"2030-01-01T00:00:00Z","publicKey":"pk","intentSignKey":"k"}""".encodeToByteArray())
            }
            // the first try of every call is sent before any is answered: all carry the old token
            if (headers["Authorization"] == "Bearer old" && ++waiting <= callsAtOnce) {
                if (waiting == callsAtOnce) allSent.complete(Unit) else allSent.await()
            }
            return if (headers["Authorization"] == "Bearer $accessToken") Answer(200, """{"count":1}""".encodeToByteArray())
            else Answer(401, """{"error":"expired"}""".encodeToByteArray())
        }
    }

    @BeforeTest
    fun setUp() {
        JsonOutputFactory.installCommon()
        JsonInputFactory.installCommon()
    }

    @Test
    fun callsRefusedTogether_renewTheSessionOnce_andAllGoThrough() = runBlocking {
        val server = FakeServer(callsAtOnce = 3)
        val config = RestConfig(server)
        val auth = RestAuthenticationService(config)
        auth.readAuthState(JsonInputFactory.createStringInput("""{"accessToken":"old","refreshToken":"r1","publicKeyBase64":"pk"}""").input)
        var lost = false
        auth.setSessionListener(object : SessionListener {
            override fun sessionRenewed() {}
            override fun sessionLost() {
                lost = true
            }
        })

        val answers = List(3) { async { server.postJson("/api/repo/product/count", "{}") } }.awaitAll()

        assertEquals(List(3) { """{"count":1}""" }, answers)
        assertEquals(1, server.renewals, "one renewal for all")
        assertFalse(lost, "the session was kept")
    }
}
