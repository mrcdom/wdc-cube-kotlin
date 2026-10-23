package br.com.wdc.framework.rest.testing

import br.com.wdc.framework.rest.RestApi
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Sobe uma [RestApi] em processo, numa porta livre, e bate nela por HTTP de verdade — com o mesmo pipeline,
 * segurança, CORS e compressão da produção.
 *
 * ```kotlin
 * RestTestServer(api).use { server ->
 *     val response = server.client().bearer(token).get("/order/1")
 *     assertEquals(200, response.status)
 *     assertEquals("open", response.json().asJsonObject["status"].asString)
 * }
 * ```
 */
class RestTestServer(api: RestApi, configure: (JavalinConfig) -> Unit = {}) : AutoCloseable {

    private val javalin: Javalin = api.createServer(configure).start(0)

    val port: Int get() = javalin.port()

    val baseUrl: String get() = "http://localhost:$port"

    /** Um cliente novo, sem cabeçalhos. Cada um guarda os seus ([TestClient.header]). */
    fun client(): TestClient = TestClient(baseUrl)

    override fun close() {
        javalin.stop()
    }
}

/** Cliente HTTP mínimo para os testes. Os cabeçalhos fixados valem para todas as chamadas dele. */
class TestClient(private val baseUrl: String) {

    private val headers = LinkedHashMap<String, String>()

    /** Sem descompressão automática: o teste vê o `Content-Encoding` como veio. */
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    fun header(name: String, value: String?): TestClient = apply { if (value == null) headers.remove(name) else headers[name] = value }

    fun bearer(token: String?): TestClient = header("Authorization", token?.let { "Bearer $it" })

    fun get(path: String, query: Map<String, Any?> = emptyMap()) = send("GET", path, query = query)

    fun delete(path: String, query: Map<String, Any?> = emptyMap()) = send("DELETE", path, query = query)

    fun post(path: String, json: String? = null) = send("POST", path, json)

    fun put(path: String, json: String? = null) = send("PUT", path, json)

    fun patch(path: String, json: String? = null) = send("PATCH", path, json)

    /** Qualquer verbo. Corpo de texto vai como `application/json`, salvo `Content-Type` em [extraHeaders]. */
    fun send(
        method: String,
        path: String,
        body: String? = null,
        query: Map<String, Any?> = emptyMap(),
        extraHeaders: Map<String, String> = emptyMap(),
    ): TestResponse = sendBytes(method, path, body?.toByteArray(StandardCharsets.UTF_8), query, extraHeaders, body != null)

    fun sendBytes(
        method: String,
        path: String,
        body: ByteArray?,
        query: Map<String, Any?> = emptyMap(),
        extraHeaders: Map<String, String> = emptyMap(),
        json: Boolean = false,
    ): TestResponse {
        val queryString = query.filterValues { it != null }.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v.toString(), StandardCharsets.UTF_8)
        }
        val uri = URI(baseUrl + path + if (queryString.isEmpty()) "" else (if ('?' in path) "&" else "?") + queryString)
        val builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
        val all = LinkedHashMap(headers).apply { putAll(extraHeaders) }
        if (json && all.keys.none { it.equals("Content-Type", ignoreCase = true) }) all["Content-Type"] = "application/json"
        all.forEach { (name, value) -> builder.header(name, value) }
        builder.method(method, if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofByteArray(body))
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        return TestResponse(response.statusCode(), response.headers().map(), response.body())
    }
}

/** Resposta lida inteira. */
class TestResponse(val status: Int, val headers: Map<String, List<String>>, val bytes: ByteArray) {

    val body: String get() = String(bytes, StandardCharsets.UTF_8)

    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

    val contentType: String? get() = header("Content-Type")

    fun json(): JsonElement = JsonParser.parseString(body)

    override fun toString(): String = "$status $body"
}
