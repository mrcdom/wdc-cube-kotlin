package br.com.wdc.framework.rest

import br.com.wdc.framework.rest.security.BearerAuthenticator
import br.com.wdc.framework.rest.security.SimplePrincipal
import br.com.wdc.framework.rest.testing.RestTestServer
import br.com.wdc.framework.rest.testing.TestResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SecurityHeadersTest {

    private val noCache = mapOf(
        "Cache-Control" to "no-cache, no-store, max-age=0, must-revalidate",
        "Pragma" to "no-cache",
        "Expires" to "0",
    )

    private val security = mapOf(
        "X-Content-Type-Options" to "nosniff",
        "X-Frame-Options" to "DENY",
        "X-XSS-Protection" to "1; mode=block",
    )

    private fun api(block: RestApi.Builder.() -> Unit) = RestApi {
        block()
        get("/plain", public = true) { _ -> mapOf("ok" to true) }
        get("/private") { _ -> mapOf("ok" to true) }
        get("/fails", public = true) { _ -> throw IllegalStateException("boom") }
        get("/cached", public = true) { call ->
            call.header("Cache-Control", "public, max-age=60")
            "cached"
        }
        get("/cached-response", public = true) { _ -> RestResponse.ok("cached").withHeader("Cache-Control", "max-age=10") }
        get("/frame", public = true) { call ->
            call.header("X-Frame-Options", "SAMEORIGIN")
            "frame"
        }
    }

    private val secured: RestApi.Builder.() -> Unit = {
        security { authenticator = BearerAuthenticator { token -> if (token == "ok") SimplePrincipal("1") else null } }
        handleUnmatched = true
        cors { anyOrigin() }
    }

    private fun assertAll(response: TestResponse, expected: Map<String, String>) {
        expected.forEach { (name, value) -> assertEquals(value, response.header(name), "$name em $response") }
    }

    private fun assertNone(response: TestResponse, names: Collection<String>) {
        names.forEach { assertNull(response.header(it), "$it em $response") }
    }

    @Test
    fun off_byDefault() {
        RestTestServer(api(secured)).use { server ->
            assertNone(server.client().get("/plain"), noCache.keys + security.keys)
            assertNone(server.client().get("/nowhere"), noCache.keys + security.keys)
        }
    }

    @Test
    fun on_everyOutcomeOfThePipeline_carriesTheSix() {
        RestTestServer(api { secured(); securityHeaders() }).use { server ->
            val client = server.client()
            val all = noCache + security

            val ok = client.get("/plain")
            assertEquals(200, ok.status)
            assertAll(ok, all)

            val error = client.get("/fails")
            assertEquals(500, error.status)
            assertAll(error, all)

            val unauthenticated = client.get("/private")
            assertEquals(401, unauthenticated.status)
            assertAll(unauthenticated, all)

            val notFound = client.bearer("ok").get("/nowhere")
            assertEquals(404, notFound.status)
            assertAll(notFound, all)

            val preflight = server.client().send(
                "OPTIONS", "/plain",
                extraHeaders = mapOf("Origin" to "https://app.example", "Access-Control-Request-Method" to "GET"),
            )
            assertEquals(200, preflight.status)
            assertEquals("*", preflight.header("Access-Control-Allow-Origin"))
            assertAll(preflight, all)
        }
    }

    @Test
    fun preflight_withoutHandleUnmatched_carriesTheSix() {
        RestTestServer(api { cors { anyOrigin() }; securityHeaders() }).use { server ->
            val preflight = server.client().send(
                "OPTIONS", "/plain",
                extraHeaders = mapOf("Origin" to "https://app.example", "Access-Control-Request-Method" to "GET"),
            )
            assertEquals(200, preflight.status)
            assertAll(preflight, noCache + security)
        }
    }

    @Test
    fun protectUnmatched_refusal_carriesTheSix() {
        val api = api {
            security {
                authenticator = BearerAuthenticator { null }
                protectUnmatched = true
            }
            securityHeaders()
        }
        RestTestServer(api).use { server ->
            val refused = server.client().get("/nowhere")
            assertEquals(401, refused.status)
            assertAll(refused, noCache + security)
        }
    }

    @Test
    fun routeWithItsOwnCacheControl_keepsIt_withoutPragmaOrExpires() {
        RestTestServer(api { securityHeaders() }).use { server ->
            for ((path, value) in listOf("/cached" to "public, max-age=60", "/cached-response" to "max-age=10")) {
                val response = server.client().get(path)
                assertEquals(value, response.header("Cache-Control"), path)
                assertNone(response, listOf("Pragma", "Expires"))
                assertAll(response, security)
            }
        }
    }

    @Test
    fun theMap_replacesAddsAndRemoves_andNoCacheCanBeTurnedOff() {
        val api = api {
            securityHeaders {
                noCache = false
                headers["X-Frame-Options"] = "SAMEORIGIN"
                headers -= "X-XSS-Protection"
                headers["Strict-Transport-Security"] = "max-age=31536000"
            }
        }
        RestTestServer(api).use { server ->
            val response = server.client().get("/plain")
            assertAll(response, mapOf("X-Content-Type-Options" to "nosniff", "X-Frame-Options" to "SAMEORIGIN", "Strict-Transport-Security" to "max-age=31536000"))
            assertNone(response, noCache.keys + "X-XSS-Protection")
        }
    }

    @Test
    fun theConfiguredValue_winsOverTheRoutes() {
        RestTestServer(api { securityHeaders() }).use { server ->
            assertEquals("DENY", server.client().get("/frame").header("X-Frame-Options"))
        }
    }

    @Test
    fun theOpenApiDocument_carriesThemToo() {
        RestTestServer(api { securityHeaders(); openApi() }).use { server ->
            assertAll(server.client().get("/openapi.json"), noCache + security)
        }
    }
}
