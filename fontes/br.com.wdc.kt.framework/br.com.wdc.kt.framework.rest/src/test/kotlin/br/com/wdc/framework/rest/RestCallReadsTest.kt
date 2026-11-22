package br.com.wdc.framework.rest

import br.com.wdc.framework.rest.testing.RestTestServer
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The required query reads, the body's explicit nulls and the `Basic` credentials. */
class RestCallReadsTest {

    data class CustomerPatch(val name: String? = null, val phone: String? = null)

    enum class Kind { DAILY, WEEKLY }

    private val api = RestApi {
        get("/long") { call -> call.requiredQueryLong("id") }
        get("/int") { call -> call.requiredQueryInt("page") }
        get("/uuid") { call -> call.requiredQueryUuid("key").toString() }
        get("/date") { call -> call.requiredQueryLocalDate("day").toString() }
        get("/enum") { call -> call.requiredQueryEnum<Kind>("kind").name }
        patch("/customer") { call ->
            val nulls = call.explicitNulls()
            val patch = call.bodyOrNull<CustomerPatch>()
            mapOf("nulls" to nulls.toList(), "name" to patch?.name, "again" to call.explicitNulls().toList())
        }
        post("/nulls") { call -> call.explicitNulls().toList() }
        post("/token", public = true) { call ->
            val credentials = call.basicCredentials()
            if (credentials == null) "none" else "${credentials.first}|${credentials.second}"
        }
    }

    private val server = RestTestServer(api)
    private val client = server.client()

    @AfterTest
    fun stop() = server.close()

    private fun error(path: String): String {
        val response = client.get(path)
        assertEquals(400, response.status, response.body)
        return response.json().asJsonObject["error"].asString
    }

    @Test
    fun requiredQuery_typed_presentAbsentAndMalformed() {
        assertEquals("42", client.get("/long?id=42").body)
        assertEquals("3", client.get("/int?page=3").body)
        assertEquals("00000000-0000-0000-0000-000000000001", client.get("/uuid?key=00000000-0000-0000-0000-000000000001").body)
        assertEquals("2026-11-15", client.get("/date?day=2026-11-15").body)
        assertEquals("WEEKLY", client.get("/enum?kind=WEEKLY").body)

        // absent (or empty): the same message as requiredQuery
        assertEquals("Required request parameter 'id' is not present", error("/long"))
        assertEquals("Required request parameter 'id' is not present", error("/long?id="))
        assertEquals("Required request parameter 'kind' is not present", error("/enum"))

        // malformed: the same message as the nullable query*
        assertEquals("Failed to convert value 'x' of parameter 'id' to required type 'long'", error("/long?id=x"))
        assertEquals("Failed to convert value '9999999999' of parameter 'page' to required type 'int'", error("/int?page=9999999999"))
        assertEquals("Failed to convert value 'nope' of parameter 'key' to required type 'UUID'", error("/uuid?key=nope"))
        assertEquals("Failed to convert value 'weekly' of parameter 'kind' to required type 'Kind'", error("/enum?kind=weekly"))
    }

    private fun patch(body: String?) = client.send("PATCH", "/customer", body).json().asJsonObject

    @Test
    fun explicitNulls_onlyTopLevelNulls_andTheBodyStillReads() {
        val read = patch("""{"name":"Ana","phone":null,"address":{"zip":null},"tags":[null]}""")
        assertEquals(listOf("phone"), read["nulls"].asJsonArray.map { it.asString })
        assertEquals("Ana", read["name"].asString, "body<T>() still reads afterwards")
        assertEquals(listOf("phone"), read["again"].asJsonArray.map { it.asString }, "reading again gives the same")

        assertEquals(listOf("name", "phone"), patch("""{"name":null,"phone":null}""")["nulls"].asJsonArray.map { it.asString })
        assertTrue(patch("""{"name":"Ana"}""")["nulls"].asJsonArray.isEmpty, "absent is not null")
    }

    @Test
    fun explicitNulls_emptyOrNonObjectBody_isEmpty() {
        for (body in listOf(null, "   ", "null", "[null]", "\"x\"", "{not json", "{\"a\":")) {
            val response = client.send("POST", "/nulls", body)
            assertEquals("[]", response.body, "corpo: $body")
        }
        // a non-object body doesn't fit the DTO either: the 400 comes from body, not from here
        assertEquals(400, client.send("PATCH", "/customer", "{not json").status)
    }

    private fun basic(raw: String) = "Basic " + Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))

    private fun token(authorization: String?): String =
        client.send("POST", "/token", extraHeaders = authorization?.let { mapOf("Authorization" to it) } ?: emptyMap()).body

    @Test
    fun basicCredentials_decoded() {
        assertEquals("app|s3cret", token(basic("app:s3cret")))
        assertEquals("app|s3cret", token("basic " + basic("app:s3cret").substringAfter(' ')), "scheme is case-insensitive")
        assertEquals("app|s3cret", token("  BASIC   " + basic("app:s3cret").substringAfter(' ') + "  "))
        assertEquals("app|a:b:c", token(basic("app:a:b:c")), "only the first ':' separates")
        assertEquals("my app|p:w d", token(basic("my+app:p%3Aw+d")), "each part decoded as a form")
        assertEquals("app|", token(basic("app:")), "empty password")
        assertEquals("|x", token(basic(":x")))
        assertEquals("jalapeño|crème", token(basic("jalapeño:crème")), "UTF-8")
    }

    @Test
    fun basicCredentials_absentOrInvalid_isNull() {
        assertEquals("none", token(null))
        assertEquals("none", token("Bearer abc"))
        assertEquals("none", token("Basic"))
        assertEquals("none", token("Basic !!!not-base64"))
        assertEquals("none", token(basic("no-colon")))
        assertEquals("none", token(basic("app:%zz")), "invalid form encoding")
    }
}
