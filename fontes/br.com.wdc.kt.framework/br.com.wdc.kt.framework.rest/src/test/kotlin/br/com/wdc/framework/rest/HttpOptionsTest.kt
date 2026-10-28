package br.com.wdc.framework.rest

import br.com.wdc.framework.rest.security.BearerAuthenticator
import br.com.wdc.framework.rest.testing.RestTestServer
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
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
        // O navegador manda o preflight sem credencial; com protectUnmatched ele caía como rota inexistente e levava 403.
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

            // OPTIONS sem cara de preflight continua protegido, e a rota continua exigindo credencial.
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
