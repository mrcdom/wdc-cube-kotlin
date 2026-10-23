package br.com.wdc.framework.rest.openapi

import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.security.BearerAuthenticator
import br.com.wdc.framework.rest.testing.RestTestServer
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenApiGeneratorTest {

    enum class Status { ACTIVE, BLOCKED }

    data class Customer(val id: Long, val name: String, val birthday: LocalDate?, @SerializedName("credit_limit") val creditLimit: BigDecimal, val status: Status)

    data class SearchResponse<T>(val count: Int, val elements: List<T>)

    private val api = RestApi {
        security {
            authenticator = BearerAuthenticator { null }
            permitAll("GET /catalog/featured")
        }
        openApi {
            path = "/v3/api-docs"
            title = "Orders"
            tag("Customer", "Clientes")
        }
        group("/customer", tags = listOf("Customer")) {
            get("/{id}", doc = {
                summary = "Busca o cliente"
                path("id", Long::class.java)
                query("full", Boolean::class.java)
                returns<Customer>(200)
                response(404)
            }) { _ -> null }
            post("/search", doc = {
                accepts<Map<String, String>>()
                returns<SearchResponse<Customer>>()
            }) { _ -> null }
        }
        get("/catalog/featured") { _ -> null }
        get("/hidden", hidden = true) { _ -> null }
        get("/files/<path>", doc = { raw(mapOf("summary" to "à mão", "x-custom" to true)) }) { _ -> null }
    }

    private fun spec(): JsonObject = JsonParser.parseString(api.openApiJson).asJsonObject

    @Test
    fun everyRegisteredRoute_isDocumented_unlessHidden() {
        val paths = spec().getAsJsonObject("paths")
        assertEquals(setOf("/customer/{id}", "/customer/search", "/catalog/featured", "/files/{path}"), paths.keySet())
        assertTrue(paths.getAsJsonObject("/customer/search").has("post"))
    }

    @Test
    fun parameters_fromThePath_andDeclared() {
        val op = spec().getAsJsonObject("paths").getAsJsonObject("/customer/{id}").getAsJsonObject("get")
        val params = op.getAsJsonArray("parameters").map { it.asJsonObject }
        assertEquals(listOf("id" to "path", "full" to "query"), params.map { it["name"].asString to it["in"].asString })
        assertEquals("int64", params[0].getAsJsonObject("schema")["format"].asString)
        assertEquals(listOf("Customer"), op.getAsJsonArray("tags").map { it.asString })

        val files = spec().getAsJsonObject("paths").getAsJsonObject("/files/{path}").getAsJsonObject("get")
        assertEquals("path", files.getAsJsonArray("parameters")[0].asJsonObject["name"].asString)
        assertEquals("à mão", files["summary"].asString)
        assertTrue(files["x-custom"].asBoolean)
    }

    @Test
    fun schemas_fromTheDtoClasses_withTheJsonNames() {
        val schemas = spec().getAsJsonObject("components").getAsJsonObject("schemas")
        val customer = schemas.getAsJsonObject("Customer").getAsJsonObject("properties")
        assertEquals(setOf("id", "name", "birthday", "credit_limit", "status"), customer.keySet())
        assertEquals("date", customer.getAsJsonObject("birthday")["format"].asString)
        assertEquals(listOf("ACTIVE", "BLOCKED"), customer.getAsJsonObject("status").getAsJsonArray("enum").map { it.asString })

        val page = schemas.getAsJsonObject("SearchResponseCustomer").getAsJsonObject("properties")
        assertEquals("#/components/schemas/Customer", page.getAsJsonObject("elements").getAsJsonObject("items")["\$ref"].asString)
    }

    @Test
    fun security_publicRoutesOptOut() {
        val spec = spec()
        assertTrue(spec.has("security"))
        assertTrue(spec.getAsJsonObject("components").getAsJsonObject("securitySchemes").has("bearerAuth"))
        val paths = spec.getAsJsonObject("paths")
        assertEquals(0, paths.getAsJsonObject("/catalog/featured").getAsJsonObject("get").getAsJsonArray("security").size())
        assertFalse(paths.getAsJsonObject("/customer/{id}").getAsJsonObject("get").has("security"))
    }

    @Test
    fun servedPublicly_atTheConfiguredPath() {
        RestTestServer(api).use { server ->
            val response = server.client().get("/v3/api-docs")
            assertEquals(200, response.status)
            assertEquals(spec(), response.json())
        }
    }
}
