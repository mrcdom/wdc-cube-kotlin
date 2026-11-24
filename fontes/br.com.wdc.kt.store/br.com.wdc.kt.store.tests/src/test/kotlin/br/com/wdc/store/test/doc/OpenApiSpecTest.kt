package br.com.wdc.store.test.doc

import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.criteria.Operator
import br.com.wdc.store.domain.product.ProductCriteria
import br.com.wdc.store.domain.purchase.PurchaseCriteria
import br.com.wdc.store.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.store.domain.user.UserCriteria
import br.com.wdc.store.persistence.rest.RepositoryApiRoutes
import br.com.wdc.store.test.util.RestTestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * The OpenAPI description keeps telling the truth: it is valid JSON, it does not point to what does not exist, and
 * the criteria fields, orderings and routes it documents are the ones the domain and the server really have.
 */
class OpenApiSpecTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-store-rest-openapi", jwtSecret = "test-secret-with-enough-length-0123456789")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)

        private val REF = Regex("\"\\\$ref\"\\s*:\\s*\"#/components/(schemas|parameters)/([^\"]+)\"")
    }

    private class Case(val schema: String, val criteria: Criteria, val orderings: List<Enum<*>>)

    private val cases = listOf(
        Case("ProductCriteria", ProductCriteria(), ProductCriteria.OrderBy.entries),
        Case("UserCriteria", UserCriteria(), UserCriteria.OrderBy.entries),
        Case("PurchaseCriteria", PurchaseCriteria(), PurchaseCriteria.OrderBy.entries),
        Case("PurchaseItemCriteria", PurchaseItemCriteria(), PurchaseItemCriteria.OrderBy.entries),
    )

    @AfterEach
    fun signOut() = env.logout()

    /** The API document as the environment's server builds it (with security on). */
    private fun json(): String = RepositoryApiRoutes.create().openApiJson!!

    private fun spec(): JsonObject = JsonParser.parseString(json()).asJsonObject

    private fun schemas(): JsonObject = spec().getAsJsonObject("components").getAsJsonObject("schemas")

    private fun send(method: String, path: String, body: String? = null, token: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${env.port}$path"))
        token?.let { builder.header("Authorization", "Bearer $it") }
        val publisher = if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body)
        builder.method(method, publisher)
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun isValidJson_withTheOpenApiSections() {
        val spec = spec()
        assertEquals("3.0.3", spec.get("openapi").asString)
        assertTrue(spec.has("info"))
        assertTrue(spec.has("paths"))
        assertTrue(spec.has("components"))
    }

    @Test
    fun isServedPublicly_atOpenapiJson() {
        val response = send("GET", "/openapi.json")
        assertEquals(200, response.statusCode())
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))
        assertEquals(JsonParser.parseString(json()), JsonParser.parseString(response.body()))
    }

    @Test
    fun everyReferencePointsToSomethingThatExists() {
        val components = spec().getAsJsonObject("components")
        val references = REF.findAll(json()).map { it.groupValues[1] to it.groupValues[2] }.toSet()
        assertTrue(references.size > 20, "too few references: ${references.size}")
        val broken = references.filterNot { (kind, name) -> components.getAsJsonObject(kind).has(name) }
        assertEquals(emptyList<Pair<String, String>>(), broken)
    }

    @Test
    fun criteriaFieldsAreTheOnesOfTheDomain() {
        // Checks against criterions(), not against a list written here: a fixed list would age together with the
        // documentation, and both would come to agree on what is no longer true.
        for (case in cases) {
            val properties = schemas().getAsJsonObject(case.schema).getAsJsonObject("properties")
            val documented = properties.keySet().filter { it != "orderBy" }
            assertEquals(case.criteria.criterions().map { it.name }, documented, case.schema)
        }
    }

    @Test
    fun orderingsAreTheConstantsOfEachEntity() {
        for (case in cases) {
            val orderBy = schemas().getAsJsonObject(case.schema).getAsJsonObject("properties").getAsJsonObject("orderBy")
            assertEquals(case.orderings.map { it.name }, orderBy.getAsJsonArray("enum").map { it.asString }, case.schema)
        }
    }

    @Test
    fun operatorsAreTheOnesTheApiAccepts() {
        val documented = schemas().getAsJsonObject("CriterionPredicate")
            .getAsJsonObject("properties").getAsJsonObject("o").getAsJsonArray("enum").map { it.asString }
        assertEquals(Operator.entries.map { it.name }, documented)

        val criterion = schemas().getAsJsonObject("Criterion").getAsJsonObject("properties")
        assertTrue(criterion.has("or"))
        assertTrue(criterion.has("p"))
    }

    @Test
    fun projectedCollection_andBothFormsOfItems_areDocumented() {
        val envelope = schemas().getAsJsonObject("ProjectedCollection").getAsJsonObject("properties")
        assertEquals(setOf("shape", "where", "limit", "offset"), envelope.keySet())

        // items is an array in the response and an envelope in the projection — describing only one of the forms would mislead
        val forms = schemas().getAsJsonObject("Purchase").getAsJsonObject("properties").getAsJsonObject("items").getAsJsonArray("oneOf").toString()
        assertTrue("PurchaseItem" in forms)
        assertTrue("ProjectedCollection" in forms)
    }

    @Test
    fun password_isDocumentedAsWriteOnly() {
        val password = schemas().getAsJsonObject("User").getAsJsonObject("properties").getAsJsonObject("password")
        assertTrue(password.get("writeOnly").asBoolean)
    }

    @Test
    fun queryOperationsPointToTheBodyOfTheirEntity() {
        val paths = spec().getAsJsonObject("paths")
        for ((entity, route) in listOf("Product" to "product", "User" to "user", "Purchase" to "purchase", "PurchaseItem" to "purchase-item")) {
            fun requestOf(operation: String) = paths.getAsJsonObject("/api/repo/$route/$operation").getAsJsonObject("post")
                .getAsJsonObject("requestBody").toString()
            assertTrue("${entity}FetchRequest" in requestOf("fetch"), entity)
            assertTrue("${entity}FetchPageRequest" in requestOf("fetch-page"), entity)
            assertTrue("${entity}Criteria" in requestOf("count"), entity)
            assertTrue("${entity}Criteria" in requestOf("delete"), entity)
        }
    }

    @Test
    fun insertIsDocumentedWithTheStatusItAnswers() {
        val responses = spec().getAsJsonObject("paths").getAsJsonObject("/api/repo/product/insert").getAsJsonObject("post").getAsJsonObject("responses")
        assertTrue(responses.has("200"))
        assertFalse(responses.has("201"))

        env.loginAs("admin")
        val response = send("POST", "/api/repo/product/insert", """{"name":"doc","price":1.5,"description":"d"}""", env.authClient!!.accessToken)
        assertEquals(200, response.statusCode())
    }

    @Test
    fun everyDocumentedRoute_isServed_andTransactionRoutesAreDocumented() {
        val paths = spec().getAsJsonObject("paths")
        for (route in listOf("/api/tx/begin", "/api/tx/commit", "/api/tx/rollback", "/api/tx/status")) {
            assertTrue(paths.has(route), "route not documented: $route")
        }

        env.loginAs("admin")
        val token = env.authClient!!.accessToken
        fun served(response: HttpResponse<String>) =
            // a 404 with the application's error body is the route's answer ("row not found"); Javalin's is "no route"
            response.statusCode() != 405 && !(response.statusCode() == 404 && "Endpoint" in response.body())
        // the check tells apart the route (or the method) that does not exist
        for ((method, path) in listOf("POST" to "/api/repo/product/no-such-operation", "GET" to "/api/repo/product/fetch/x", "PUT" to "/api/tx/begin")) {
            assertFalse(served(send(method, path, if (method == "GET") null else "{}", token)), "$method $path")
        }

        val notServed = ArrayList<String>()
        var openTx: String? = null
        // begin goes last: with an open transaction, writes without X-Tx-Id would be refused
        val routes = paths.keySet().sortedBy { it == "/api/tx/begin" }
        for (path in routes) {
            for (method in paths.getAsJsonObject(path).keySet()) {
                val url = path.replace("{id}", "0")
                val response = send(method.uppercase(), url, if (method == "get") null else "{}", token)
                if (!served(response)) notServed.add("$method $path → ${response.statusCode()}")
                if (path == "/api/tx/begin") {
                    openTx = Regex("\"txId\":\"([^\"]+)\"").find(response.body())?.groupValues?.get(1)
                }
            }
        }
        assertNotNull(openTx, "begin did not open a transaction")
        val rollback = HttpRequest.newBuilder(URI("http://127.0.0.1:${env.port}/api/tx/rollback"))
            .header("Authorization", "Bearer $token").header("X-Tx-Id", openTx).POST(HttpRequest.BodyPublishers.ofString("{}")).build()
        assertEquals(200, HttpClient.newHttpClient().send(rollback, HttpResponse.BodyHandlers.ofString()).statusCode())

        assertEquals(emptyList<String>(), notServed)
    }

    @Test
    fun descriptionsAreFreeOfPlaceholders() {
        val json = json()
        assertFalse("TODO" in json)
        assertFalse("FIXME" in json)
    }
}
