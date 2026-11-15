package br.com.wdc.framework.rest

import br.com.wdc.framework.rest.security.BearerAuthenticator
import br.com.wdc.framework.rest.security.SimplePrincipal
import br.com.wdc.framework.rest.testing.RestTestServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerTimingTest {

    private val total = Regex("""total;dur=\d+\.\d""")

    private fun api(block: RestApi.Builder.() -> Unit) = RestApi {
        block()
        get("/plain") { _ -> mapOf("ok" to true) }
        get("/measured") { call ->
            call.timing("db", 2_500_000L, "OrderQuery")
            call.timed("action", "CreateOrderAction") { mapOf("ok" to true) }
        }
        get("/fails") { call ->
            call.timing("action", 1_000_000L)
            throw IllegalStateException("boom")
        }
    }

    @Test
    fun off_byDefault_noHeader() {
        RestTestServer(api { }).use { server ->
            assertNull(server.client().get("/measured").header("Server-Timing"))
        }
    }

    @Test
    fun on_totalOnSuccess() {
        RestTestServer(api { serverTiming() }).use { server ->
            val header = server.client().get("/plain").header("Server-Timing")!!
            assertTrue(Regex("""^total;dur=\d+\.\d$""").matches(header), header)
        }
    }

    @Test
    fun routeMeasures_comeBeforeTotal_inRegistrationOrder_withoutNamesByDefault() {
        RestTestServer(api { serverTiming() }).use { server ->
            val header = server.client().get("/measured").header("Server-Timing")!!
            assertTrue(Regex("""^db;dur=2\.5, action;dur=\d+\.\d, total;dur=\d+\.\d$""").matches(header), header)
        }
    }

    @Test
    fun names_onlyWhenEnabled() {
        RestTestServer(api { serverTiming { names = true } }).use { server ->
            val header = server.client().get("/measured").header("Server-Timing")!!
            assertTrue(
                Regex("""^db;desc="OrderQuery";dur=2\.5, action;desc="CreateOrderAction";dur=\d+\.\d, total;dur=\d+\.\d$""").matches(header),
                header,
            )
        }
    }

    @Test
    fun errorResponses_carryTheHeaderToo() {
        RestTestServer(api { serverTiming() }).use { server ->
            val response = server.client().get("/fails")
            assertEquals(500, response.status)
            val header = response.header("Server-Timing")!!
            assertTrue(Regex("""^action;dur=1\.0, total;dur=\d+\.\d$""").matches(header), header)
        }
    }

    @Test
    fun unmatchedPath_withProtectUnmatched_carriesTotal() {
        val secured = api {
            serverTiming()
            security {
                authenticator = BearerAuthenticator { token -> if (token == "ok") SimplePrincipal("1") else null }
                unauthenticatedStatus = 403
                protectUnmatched = true
            }
        }
        RestTestServer(secured).use { server ->
            val notFound = server.client().bearer("ok").get("/nowhere")
            assertEquals(404, notFound.status)
            assertTrue(total.matches(notFound.header("Server-Timing")!!), notFound.header("Server-Timing"))

            val refused = server.client().get("/nowhere")
            assertEquals(403, refused.status)
            assertTrue(total.matches(refused.header("Server-Timing")!!), refused.header("Server-Timing"))
        }
    }

    @Test
    fun cors_exposesTheHeader() {
        RestTestServer(api { serverTiming(); cors { anyOrigin(); exposedHeaders += "X-Other" } }).use { server ->
            val preflight = server.client().send(
                "OPTIONS", "/plain",
                extraHeaders = mapOf("Origin" to "https://app.example", "Access-Control-Request-Method" to "GET"),
            )
            val response = server.client().header("Origin", "https://app.example").get("/plain")
            val exposed = preflight.header("Access-Control-Expose-Headers")!!
            assertTrue("Server-Timing" in exposed && "X-Other" in exposed, exposed)
            assertTrue("Server-Timing" in response.header("Access-Control-Expose-Headers")!!)
        }
    }
}
