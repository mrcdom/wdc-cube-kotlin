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
 * Starts a [RestApi] in process, on a free port, and hits it over real HTTP — with the same pipeline,
 * security, CORS and compression as production.
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

    // Loopback only: on all interfaces, the system may hand out a port another process already listens on at
    // 127.0.0.1 (macOS allows it, with SO_REUSEADDR), and the client, which calls loopback, lands on the other process.
    private val javalin: Javalin = api.createServer(configure).start(HOST, 0)

    val port: Int get() = javalin.port()

    val baseUrl: String get() = "http://$HOST:$port"

    /** A new client, without headers. Each one keeps its own ([TestClient.header]). */
    fun client(): TestClient = TestClient(baseUrl)

    override fun close() {
        javalin.stop()
    }

    private companion object {
        const val HOST = "127.0.0.1"
    }
}

/** Minimal HTTP client for tests. The headers set apply to all of its calls. */
class TestClient(private val baseUrl: String) {

    private val headers = LinkedHashMap<String, String>()

    /** No automatic decompression: the test sees the `Content-Encoding` as it came. */
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    fun header(name: String, value: String?): TestClient = apply { if (value == null) headers.remove(name) else headers[name] = value }

    fun bearer(token: String?): TestClient = header("Authorization", token?.let { "Bearer $it" })

    fun get(path: String, query: Map<String, Any?> = emptyMap()) = send("GET", path, query = query)

    fun delete(path: String, query: Map<String, Any?> = emptyMap()) = send("DELETE", path, query = query)

    fun post(path: String, json: String? = null) = send("POST", path, json)

    fun put(path: String, json: String? = null) = send("PUT", path, json)

    fun patch(path: String, json: String? = null) = send("PATCH", path, json)

    /** Any verb. A text body goes as `application/json`, unless `Content-Type` is in [extraHeaders]. */
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

/** A fully read response. */
class TestResponse(val status: Int, val headers: Map<String, List<String>>, val bytes: ByteArray) {

    val body: String get() = String(bytes, StandardCharsets.UTF_8)

    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

    val contentType: String? get() = header("Content-Type")

    fun json(): JsonElement = JsonParser.parseString(body)

    override fun toString(): String = "$status $body"
}
