package br.com.wdc.framework.rest.openapi

import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.security.BearerAuthenticator
import br.com.wdc.framework.rest.testing.RestTestServer
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwaggerUiTest {

    private fun api(ui: SwaggerUiOptions.() -> Unit = {}, more: RestApi.Builder.() -> Unit = {}) = RestApi {
        security {
            authenticator = BearerAuthenticator { null }
            protectUnmatched = true
        }
        handleUnmatched = true
        openApi {
            title = "Orders"
            ui(ui)
        }
        get("/order/{id}") { call -> call.pathLong("id") }
        more()
    }

    /** O objeto de configuração passado ao `SwaggerUIBundle`. */
    private fun config(html: String): JsonObject {
        val json = Regex("""Object\.assign\((\{.*?\}), \{""", RegexOption.DOT_MATCHES_ALL).find(html)!!.groupValues[1]
        return JsonParser.parseString(json).asJsonObject
    }

    @Test
    fun html_redirectsToThePage() {
        RestTestServer(api()).use { server ->
            val response = server.client().get("/swagger-ui.html")
            assertEquals(302, response.status)
            assertEquals("/swagger-ui/index.html", response.header("Location"))
        }
    }

    @Test
    fun page_withTheOwnDocument_isPublic() {
        RestTestServer(api()).use { server ->
            val page = server.client().get("/swagger-ui/index.html")
            assertEquals(200, page.status, "pública, mesmo com protectUnmatched")
            assertTrue(page.contentType!!.startsWith("text/html"), page.contentType)
            assertTrue("<title>Orders</title>" in page.body)
            assertTrue("""src="swagger-ui-bundle.js"""" in page.body)
            val config = config(page.body)
            assertEquals("/openapi.json", config["url"].asString)
            assertFalse(config.has("urls"))
        }
    }

    @Test
    fun page_withSeveralDocuments_listsThem_andMarksThePrimary() {
        val ui: SwaggerUiOptions.() -> Unit = {
            title = "Docs </title><script>"
            document("/partner/openapi.json", "Partner's API")
            primary = "Partner's API"
        }
        RestTestServer(api(ui)).use { server ->
            val body = server.client().get("/swagger-ui/index.html").body
            assertTrue("<title>Docs &lt;/title&gt;&lt;script&gt;</title>" in body, "título escapado")
            assertFalse("Partner's" in body, "o JSON não traz aspas simples cruas")
            val config = config(body)
            assertEquals(
                listOf("/openapi.json" to "Orders", "/partner/openapi.json" to "Partner's API"),
                config["urls"].asJsonArray.map { it.asJsonObject["url"].asString to it.asJsonObject["name"].asString },
            )
            assertEquals("Partner's API", config["urls.primaryName"].asString)
            assertFalse(config.has("url"))
        }
    }

    @Test
    fun withoutTheOwnDocument_andAnotherPath() {
        val ui: SwaggerUiOptions.() -> Unit = {
            path = "/docs"
            includeOwn = false
            document("/a.json", "A")
            document("/b.json", "B")
        }
        RestTestServer(api(ui)).use { server ->
            assertEquals("/docs/index.html", server.client().get("/docs.html").header("Location"))
            val config = config(server.client().get("/docs/index.html").body)
            assertEquals(listOf("A", "B"), config["urls"].asJsonArray.map { it.asJsonObject["name"].asString })
            assertEquals("A", config["urls.primaryName"].asString, "sem primary, o primeiro")
            assertEquals(401, server.client().get("/swagger-ui/index.html").status, "o caminho padrão não existe (e é protegido)")
        }
    }

    @Test
    fun listedFiles_areServed_withTypeAndCache() {
        RestTestServer(api()).use { server ->
            val expected = mapOf(
                "swagger-ui.css" to "text/css",
                "swagger-ui-bundle.js" to "text/javascript",
                "swagger-ui-standalone-preset.js" to "text/javascript",
                "favicon-32x32.png" to "image/png",
            )
            for ((file, type) in expected) {
                val response = server.client().get("/swagger-ui/$file")
                assertEquals(200, response.status, file)
                assertTrue(response.contentType!!.startsWith(type), "$file: ${response.contentType}")
                assertEquals("public, max-age=86400", response.header("Cache-Control"), file)
                assertTrue(response.bytes.size > 100, file)
            }
        }
    }

    @Test
    fun otherFiles_andTraversal_areNotServed() {
        RestTestServer(api()).use { server ->
            // existem no webjar, mas a página não os carrega
            for (file in listOf("index.css", "swagger-initializer.js", "swagger-ui.js", "oauth2-redirect.html")) {
                assertEquals(404, server.client().get("/swagger-ui/$file").status, file)
            }
            for (path in listOf("/swagger-ui/..%2F..%2Fmaven%2Forg.webjars%2Fswagger-ui%2Fpom.properties", "/swagger-ui/%2e%2e")) {
                val status = server.client().get(path).status
                assertTrue(status == 404 || status == 400, "$path: $status")
            }
            // sem normalizar no cliente: o caminho chega ao servidor como foi escrito
            assertFalse(rawGet(server.port, "/swagger-ui/../../META-INF/MANIFEST.MF").startsWith("HTTP/1.1 200"))
        }
    }

    private fun rawGet(port: Int, path: String): String = Socket("localhost", port).use { socket ->
        socket.getOutputStream().write("GET $path HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray())
        socket.getInputStream().bufferedReader().readLine() ?: ""
    }

    @Test
    fun theRoutes_stayOutOfTheDocument() {
        val document = api().openApiJson!!
        assertFalse("swagger-ui" in document, document)
        assertTrue("/order/{id}" in document)
    }

    @Test
    fun unknownPrimary_failsWhenBuilding() {
        assertFailsWith<IllegalArgumentException> { api({ primary = "Nope" }) }
    }
}
