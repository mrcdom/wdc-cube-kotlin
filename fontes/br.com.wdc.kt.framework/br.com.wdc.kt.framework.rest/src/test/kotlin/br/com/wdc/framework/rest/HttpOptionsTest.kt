package br.com.wdc.framework.rest

import br.com.wdc.framework.rest.security.BearerAuthenticator
import br.com.wdc.framework.rest.testing.RestTestServer
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HttpOptionsTest {

    private fun api(block: RestApi.Builder.() -> Unit) = RestApi {
        block()
        get("/big") { _ -> mapOf("data" to "x".repeat(5000)) }
        get("/small") { _ -> mapOf("data" to "x") }
        post("/upload") { call -> call.bodyText().length }
    }

    @Test
    fun cors_anyOrigin_withPreflight() {
        RestTestServer(api { cors { anyOrigin(); maxAge = 3600 } }).use { server ->
            val response = server.client().header("Origin", "https://app.example").get("/small")
            assertEquals("*", response.header("Access-Control-Allow-Origin"))

            val preflight = server.client().send(
                "OPTIONS", "/small",
                extraHeaders = mapOf("Origin" to "https://app.example", "Access-Control-Request-Method" to "DELETE", "Access-Control-Request-Headers" to "X-Timezone"),
            )
            assertEquals(200, preflight.status)
            assertEquals("*", preflight.header("Access-Control-Allow-Origin"))
            assertEquals("3600", preflight.header("Access-Control-Max-Age"))
        }
    }

    @Test
    fun cors_preflightPassesProtectUnmatched() {
        // The browser sends the preflight without a credential; with protectUnmatched it used to fall as a nonexistent route and get 403.
        val secured = api {
            cors { anyOrigin() }
            security {
                authenticator = BearerAuthenticator { null }
                unauthenticatedStatus = 403
                protectUnmatched = true
            }
        }
        RestTestServer(secured).use { server ->
            val preflight = server.client().send(
                "OPTIONS", "/small",
                extraHeaders = mapOf("Origin" to "https://app.example", "Access-Control-Request-Method" to "GET", "Access-Control-Request-Headers" to "Authorization"),
            )
            assertEquals(200, preflight.status)
            assertEquals("*", preflight.header("Access-Control-Allow-Origin"))

            // An OPTIONS that doesn't look like a preflight stays protected, and the route still requires a credential.
            assertEquals(403, server.client().send("OPTIONS", "/small").status)
            assertEquals(403, server.client().header("Origin", "https://app.example").get("/small").status)
        }
    }

    @Test
    fun cors_restrictedOrigins() {
        RestTestServer(api { cors { allowOrigins("https://ok.example"); allowCredentials = true } }).use { server ->
            assertEquals("https://ok.example", server.client().header("Origin", "https://ok.example").get("/small").header("Access-Control-Allow-Origin"))
            assertNull(server.client().header("Origin", "https://evil.example").get("/small").header("Access-Control-Allow-Origin"))
        }
    }

    @Test
    fun cors_notAllowedOrigin_isAnsweredWithoutTheHeaders_notRefused() {
        RestTestServer(api { cors { allowOrigins("https://ok.example"); allowCredentials = true } }).use { server ->
            val response = server.client().header("Origin", "https://evil.example").get("/small")
            assertEquals(200, response.status)
            assertNull(response.header("Access-Control-Allow-Origin"))
            assertNull(response.header("Access-Control-Allow-Credentials"))

            val preflight = server.client().send(
                "OPTIONS", "/small",
                extraHeaders = mapOf("Origin" to "https://evil.example", "Access-Control-Request-Method" to "DELETE"),
            )
            assertNull(preflight.header("Access-Control-Allow-Origin"))
            assertNull(preflight.header("Access-Control-Allow-Methods"))
        }
    }

    @Test
    fun cors_sameOrigin_passesWhateverTheAllowedOrigins() {
        // a browser sends Origin on its own pages' module scripts and POSTs: the server's own origin is not CORS
        RestTestServer(api { cors { allowOrigins("https://ok.example"); allowCredentials = true } }).use { server ->
            val response = server.client().header("Origin", server.baseUrl).post("/upload", "abc")
            assertEquals(200, response.status)
            assertEquals("3", response.body)
            assertNull(response.header("Access-Control-Allow-Origin"))
        }
    }

    @Test
    fun cors_originsWithoutScheme_andWildcardSubdomains() {
        RestTestServer(api { cors { allowOrigins("ok.example", "https://*.apps.example", "http://localhost:3000", "tauri://localhost") } }).use { server ->
            fun allowOrigin(origin: String) = server.client().header("Origin", origin).get("/small").header("Access-Control-Allow-Origin")
            assertEquals("https://ok.example", allowOrigin("https://ok.example"))
            assertNull(allowOrigin("http://ok.example"), "no scheme means https")
            assertEquals("https://a.apps.example", allowOrigin("https://a.apps.example"))
            assertNull(allowOrigin("https://apps.example"), "the wildcard is for subdomains")
            assertEquals("http://localhost:3000", allowOrigin("http://localhost:3000"))
            assertNull(allowOrigin("http://localhost:3001"), "the port is part of the origin")
            assertEquals("tauri://localhost", allowOrigin("tauri://localhost"), "an app's own scheme, without a port")
            assertNull(allowOrigin("null"))
        }
    }

    @Test
    fun cors_anyOriginWithCredentials_isAConfigurationError() {
        assertFailsWith<IllegalArgumentException> {
            RestTestServer(api { cors { anyOrigin(); allowCredentials = true } }).close()
        }
    }

    @Test
    fun gzip_aboveTheMinimumSize() {
        RestTestServer(api { compression { minSize = 2048 } }).use { server ->
            val big = server.client().header("Accept-Encoding", "gzip").get("/big")
            assertEquals("gzip", big.header("Content-Encoding"))
            val text = GZIPInputStream(ByteArrayInputStream(big.bytes)).readBytes().decodeToString()
            assertEquals(5000 + """{"data":""}""".length, text.length)

            assertNull(server.client().header("Accept-Encoding", "gzip").get("/small").header("Content-Encoding"))
        }
    }

    @Test
    fun maxRequestSize_refusesLargerBodies() {
        RestTestServer(api { maxRequestSize = 1000 }).use { server ->
            assertEquals("500", server.client().post("/upload", "y".repeat(500)).body)
            assertEquals(413, server.client().post("/upload", "y".repeat(5000)).status)
        }
    }
}
