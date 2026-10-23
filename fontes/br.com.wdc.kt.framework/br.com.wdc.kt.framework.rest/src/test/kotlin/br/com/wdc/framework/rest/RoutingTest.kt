package br.com.wdc.framework.rest

import br.com.wdc.framework.rest.testing.RestTestServer
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutingTest {

    data class Item(val id: Long, val name: String, val tags: List<String> = emptyList())

    enum class Kind { DAILY, WEEKLY }

    private val api = RestApi {
        get("/items/{id}") { call -> Item(call.pathLong("id"), "item") }
        get("/items") { call ->
            mapOf(
                "page" to call.queryInt("page"),
                "size" to (call.queryInt("size") ?: 20),
                "active" to call.queryBoolean("active", false),
                "kind" to call.queryEnum<Kind>("kind")?.name,
                "tags" to call.queries("tag"),
            )
        }
        get("/required") { call -> call.requiredQuery("q") }
        post("/items") { call ->
            val item = call.body<Item>()
            RestResponse.created(item.copy(id = 7)).withHeader("Location", "/items/7")
        }
        post("/batch") { call -> call.body<List<Item>>().size }
        put("/items/{id}") { call -> call.body<Item>().copy(id = call.pathLong("id")) }
        patch("/items/{id}") { call -> mapOf("patched" to call.pathLong("id")) }
        delete("/items/{id}") { _ -> RestResponse.noContent() }
        route("HEAD", "/ping") { _ -> null }
        get("/uuid/{key}") { call -> call.pathUuid("key").toString().length }
        get("/text") { _ -> "plain" }
        get("/bool") { _ -> true }
        get("/empty") { _ -> Unit }
        get("/bytes") { _ -> byteArrayOf(1, 2, 3) }
        get("/files/<path>") { call -> call.path("path") }
        get("/cache") { call ->
            call.header("Cache-Control", "no-store")
            mapOf("ok" to true)
        }
        group("/v1", tags = listOf("V1")) {
            group("/nested") {
                get("/hello") { _ -> "hi" }
            }
        }
    }

    private val server = RestTestServer(api)
    private val client = server.client()

    @AfterTest
    fun stop() = server.close()

    @Test
    fun pathParameter_isTyped_andMalformedIs400() {
        val ok = client.get("/items/42")
        assertEquals(200, ok.status)
        assertEquals(42, ok.json().asJsonObject["id"].asInt)
        assertTrue(ok.contentType!!.startsWith("application/json"))

        val bad = client.get("/items/abc")
        assertEquals(400, bad.status)
        assertTrue("abc" in bad.json().asJsonObject["error"].asString)
    }

    @Test
    fun queryParameters_optionalWithDefaults_repeated_andEnums() {
        val response = client.get("/items?page=2&active=TRUE&kind=WEEKLY&tag=a&tag=b").json().asJsonObject
        assertEquals(2, response["page"].asInt)
        assertEquals(20, response["size"].asInt)
        assertTrue(response["active"].asBoolean)
        assertEquals("WEEKLY", response["kind"].asString)
        assertEquals(listOf("a", "b"), response["tags"].asJsonArray.map { it.asString })

        assertEquals(400, client.get("/items?kind=weekly").status, "enum é pelo nome exato")
        assertEquals(400, client.get("/items?page=x").status)
        assertEquals(400, client.get("/required").status)
        assertEquals("v", client.get("/required?q=v").body)
    }

    @Test
    fun body_isReadAsDto_includingRootArrays() {
        val created = client.post("/items", """{"id":0,"name":"novo","tags":["x"],"unknown":1}""")
        assertEquals(201, created.status)
        assertEquals("/items/7", created.header("Location"))
        assertEquals(7, created.json().asJsonObject["id"].asInt)

        assertEquals("2", client.post("/batch", """[{"id":1,"name":"a"},{"id":2,"name":"b"}]""").body)
        assertEquals(400, client.post("/items", """{"id":""").status, "JSON malformado")
        assertEquals(400, client.post("/items", "").status, "corpo exigido")
    }

    @Test
    fun everyVerb_isRouted() {
        assertEquals(5, client.put("/items/5", """{"id":0,"name":"x","tags":[]}""").json().asJsonObject["id"].asInt)
        assertEquals(9, client.patch("/items/9", "{}").json().asJsonObject["patched"].asInt)
        val deleted = client.delete("/items/1")
        assertEquals(204, deleted.status)
        assertEquals("", deleted.body)
        assertEquals(200, client.send("HEAD", "/ping").status)
        assertEquals("36", client.get("/uuid/${UUID.randomUUID()}").body)
        assertEquals(400, client.get("/uuid/nope").status)
    }

    @Test
    fun returnValues_becomeTheResponse() {
        val text = client.get("/text")
        assertEquals("plain", text.body)
        assertTrue(text.contentType!!.startsWith("text/plain"))

        assertEquals("true", client.get("/bool").body)

        val empty = client.get("/empty")
        assertEquals(200, empty.status)
        assertEquals("", empty.body)

        val bytes = client.get("/bytes")
        assertEquals(listOf<Byte>(1, 2, 3), bytes.bytes.toList())
        assertEquals("application/octet-stream", bytes.contentType)

        assertEquals("no-store", client.get("/cache").header("Cache-Control"))
    }

    @Test
    fun multiSegmentPath_groups_andTrailingSlash() {
        assertEquals("a/b/c.txt", client.get("/files/a/b/c.txt").body)
        assertEquals("hi", client.get("/v1/nested/hello").body)
        assertEquals("hi", client.get("/v1/nested/hello/").body, "barra final é ignorada")
        assertEquals(404, client.get("/nope").status)
    }
}
