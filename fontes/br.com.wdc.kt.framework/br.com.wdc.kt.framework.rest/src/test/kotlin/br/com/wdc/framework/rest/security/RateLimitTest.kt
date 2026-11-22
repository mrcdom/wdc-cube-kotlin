package br.com.wdc.framework.rest.security

import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.error.ErrorResponse
import br.com.wdc.framework.rest.testing.RestTestServer
import br.com.wdc.framework.rest.testing.TestClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RateLimitTest {

    /** Clock the test advances. */
    private class TestClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        fun advance(millis: Long) {
            now = now.plusMillis(millis)
        }
    }

    /** The test principal carries its own limit; `null` = no limit. */
    private data class Partner(override val id: String, val limit: Int?, val account: String = id) : RestPrincipal {
        override val roles: Set<String> = emptySet()
    }

    private val partners = mapOf(
        "a" to Partner("a", 3),
        "b" to Partner("b", 3),
        "free" to Partner("free", null),
        "a2" to Partner("a2", 3, account = "a"),
    )

    // 12:00:30.200: 29.8 s until the minute turns
    private val clock = TestClock(Instant.parse("2026-11-15T12:00:30.200Z"))

    private fun api(
        options: RateLimitOptions.() -> Unit = {},
        more: RestApi.Builder.() -> Unit = {},
    ) = RestApi {
        security {
            authenticator = BearerAuthenticator { token -> partners[token] }
            rateLimit {
                perMinute = { p -> (p as Partner).limit }
                clock = this@RateLimitTest.clock
                options()
            }
        }
        get("/orders") { _ -> "ok" }
        get("/catalog", public = true) { _ -> "ok" }
        more()
    }

    private fun TestClient.hit(path: String = "/orders") = get(path)

    @Test
    fun belowAndAtTheLimit_pass_aboveIs429_withRetryAfter() {
        RestTestServer(api()).use { server ->
            val client = server.client().bearer("a")
            repeat(3) { assertEquals(200, client.hit().status, "request ${it + 1}") }
            val refused = client.hit()
            assertEquals(429, refused.status)
            assertEquals("30", refused.header("Retry-After"), "29.8 s rounded up")
            assertEquals("Too many requests", refused.json().asJsonObject["error"].asString)

            clock.advance(29_000) // 12:00:59.200
            val later = client.hit()
            assertEquals(429, later.status)
            assertEquals("1", later.header("Retry-After"))
        }
    }

    @Test
    fun theNextMinute_startsOver() {
        RestTestServer(api()).use { server ->
            val client = server.client().bearer("a")
            repeat(4) { client.hit() }
            assertEquals(429, client.hit().status)
            clock.advance(29_800) // 12:01:00.000
            repeat(3) { assertEquals(200, client.hit().status) }
            assertEquals(429, client.hit().status)
            assertEquals("60", client.hit().header("Retry-After"))
        }
    }

    @Test
    fun keys_areIndependent_andTheKeyFunctionGroups() {
        RestTestServer(api()).use { server ->
            repeat(3) { server.client().bearer("a").hit() }
            assertEquals(429, server.client().bearer("a").hit().status)
            assertEquals(200, server.client().bearer("b").hit().status, "another key, another count")
        }
        // the key by account: a and a2 share the same count
        RestTestServer(api({ key = { p -> (p as Partner).account } })).use { server ->
            repeat(2) { server.client().bearer("a").hit() }
            assertEquals(200, server.client().bearer("a2").hit().status)
            assertEquals(429, server.client().bearer("a2").hit().status)
        }
    }

    @Test
    fun anonymousRequests_doNotCount_andNullLimit_doesNotLimit() {
        RestTestServer(api()).use { server ->
            repeat(10) { assertEquals(200, server.client().hit("/catalog").status) }
            repeat(10) { assertEquals(200, server.client().bearer("free").hit().status) }
            // an anonymous caller on a protected route is refused by the rule, not by the limit
            assertEquals(401, server.client().hit().status)
        }
    }

    @Test
    fun aPublicRoute_withCredential_counts() {
        RestTestServer(api()).use { server ->
            val client = server.client().bearer("a")
            repeat(3) { client.hit("/catalog") }
            assertEquals(429, client.hit("/orders").status)
        }
    }

    @Test
    fun beforeTheAccessRule() {
        val api = api(more = { get("/admin", rule = AccessRules.rule { false }) { _ -> "never" } })
        RestTestServer(api).use { server ->
            val client = server.client().bearer("a")
            repeat(3) { assertEquals(403, client.hit("/admin").status) }
            assertEquals(429, client.hit("/admin").status, "the limit comes before the rule")
        }
    }

    @Test
    fun theExceptionMapping_canRemapIt() {
        val api = api(more = {
            errors {
                on<RateLimitedException> { e, _ ->
                    ErrorResponse(503, mapOf("code" to "SLOW_DOWN", "wait" to e.retryAfterSeconds), mapOf("X-Wait" to e.retryAfterSeconds.toString()))
                }
            }
        })
        RestTestServer(api).use { server ->
            val client = server.client().bearer("a")
            repeat(3) { client.hit() }
            val refused = client.hit()
            assertEquals(503, refused.status)
            assertEquals("SLOW_DOWN", refused.json().asJsonObject["code"].asString)
            assertEquals("30", refused.header("X-Wait"))
            assertNull(refused.header("Retry-After"))
        }
    }

    @Test
    fun oldWindows_leaveTheMap() {
        val options = RateLimitOptions().apply {
            perMinute = { 10 }
            clock = this@RateLimitTest.clock
        }
        val limiter = RateLimiter(options)
        limiter.check(SimplePrincipal("x"))
        limiter.check(SimplePrincipal("y"))
        assertEquals(2, limiter.size)
        clock.advance(60_000)
        limiter.check(SimplePrincipal("z"))
        assertEquals(1, limiter.size, "the previous minute's windows were removed")
    }

    @Test
    fun withoutPrincipalOrLimit_theLimiterIsNotReached() {
        val limiter = RateLimiter(RateLimitOptions().apply { clock = this@RateLimitTest.clock })
        repeat(100) { limiter.check(SimplePrincipal("x")) }
        assertTrue(limiter.size == 0, "default perMinute is no limit, and nothing is counted")
    }
}
