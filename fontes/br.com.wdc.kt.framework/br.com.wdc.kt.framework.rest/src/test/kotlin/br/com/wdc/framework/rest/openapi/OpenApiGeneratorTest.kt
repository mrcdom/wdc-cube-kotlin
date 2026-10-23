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

    data class Patient(val id: Long, val name: String, val birthday: LocalDate?, @SerializedName("plan_value") val planValue: BigDecimal, val status: Status)

    data class SearchResponse<T>(val count: Int, val elements: List<T>)

    private val api = RestApi {
        security {
            authenticator = BearerAuthenticator { null }
            permitAll("GET /company/active")
        }
        openApi {
            path = "/v3/api-docs"
            title = "Galileo"
            tag("Patient", "Pacientes")
        }
        group("/patient", tags = listOf("Patient")) {
            get("/{id}", doc = {
                summary = "Busca o paciente"
                path("id", Long::class.java)
                query("full", Boolean::class.java)
                returns<Patient>(200)
                response(404)
            }) { _ -> null }
            post("/search", doc = {
                accepts<Map<String, String>>()
                returns<SearchResponse<Patient>>()
            }) { _ -> null }
        }
        get("/company/active") { _ -> null }
        get("/hidden", hidden = true) { _ -> null }
        get("/files/<path>", doc = { raw(mapOf("summary" to "à mão", "x-custom" to true)) }) { _ -> null }
    }

    private fun spec(): JsonObject = JsonParser.parseString(api.openApiJson).asJsonObject

    @Test
    fun everyRegisteredRoute_isDocumented_unlessHidden() {
        val paths = spec().getAsJsonObject("paths")
        assertEquals(setOf("/patient/{id}", "/patient/search", "/company/active", "/files/{path}"), paths.keySet())
        assertTrue(paths.getAsJsonObject("/patient/search").has("post"))
    }

    @Test
    fun parameters_fromThePath_andDeclared() {
        val op = spec().getAsJsonObject("paths").getAsJsonObject("/patient/{id}").getAsJsonObject("get")
        val params = op.getAsJsonArray("parameters").map { it.asJsonObject }
        assertEquals(listOf("id" to "path", "full" to "query"), params.map { it["name"].asString to it["in"].asString })
        assertEquals("int64", params[0].getAsJsonObject("schema")["format"].asString)
        assertEquals(listOf("Patient"), op.getAsJsonArray("tags").map { it.asString })

        val files = spec().getAsJsonObject("paths").getAsJsonObject("/files/{path}").getAsJsonObject("get")
        assertEquals("path", files.getAsJsonArray("parameters")[0].asJsonObject["name"].asString)
        assertEquals("à mão", files["summary"].asString)
        assertTrue(files["x-custom"].asBoolean)
    }

    @Test
    fun schemas_fromTheDtoClasses_withTheJsonNames() {
        val schemas = spec().getAsJsonObject("components").getAsJsonObject("schemas")
        val patient = schemas.getAsJsonObject("Patient").getAsJsonObject("properties")
        assertEquals(setOf("id", "name", "birthday", "plan_value", "status"), patient.keySet())
        assertEquals("date", patient.getAsJsonObject("birthday")["format"].asString)
        assertEquals(listOf("ACTIVE", "BLOCKED"), patient.getAsJsonObject("status").getAsJsonArray("enum").map { it.asString })

        val page = schemas.getAsJsonObject("SearchResponsePatient").getAsJsonObject("properties")
        assertEquals("#/components/schemas/Patient", page.getAsJsonObject("elements").getAsJsonObject("items")["\$ref"].asString)
    }

    @Test
    fun security_publicRoutesOptOut() {
        val spec = spec()
        assertTrue(spec.has("security"))
        assertTrue(spec.getAsJsonObject("components").getAsJsonObject("securitySchemes").has("bearerAuth"))
        val paths = spec.getAsJsonObject("paths")
        assertEquals(0, paths.getAsJsonObject("/company/active").getAsJsonObject("get").getAsJsonArray("security").size())
        assertFalse(paths.getAsJsonObject("/patient/{id}").getAsJsonObject("get").has("security"))
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
