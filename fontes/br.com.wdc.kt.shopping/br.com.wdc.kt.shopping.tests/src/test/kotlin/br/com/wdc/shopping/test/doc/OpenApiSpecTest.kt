package br.com.wdc.shopping.test.doc

import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.criteria.Operator
import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.persistence.rest.doc.RepositoryApiDocs
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
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
 * A descrição OpenAPI continua dizendo a verdade: é JSON válido, não aponta para o que não existe, e os campos
 * de critério, as ordenações e as rotas que ela documenta são os que o domínio e o servidor realmente têm.
 */
class OpenApiSpecTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-shopping-rest-openapi", jwtSecret = "test-secret-with-enough-length-0123456789")

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

    private fun spec(): JsonObject = JsonParser.parseString(RepositoryApiDocs.toJson()).asJsonObject

    private fun schemas(): JsonObject = spec().getAsJsonObject("components").getAsJsonObject("schemas")

    private fun send(method: String, path: String, body: String? = null, token: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:${env.port}$path"))
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
        assertEquals(JsonParser.parseString(RepositoryApiDocs.toJson()), JsonParser.parseString(response.body()))
    }

    @Test
    fun everyReferencePointsToSomethingThatExists() {
        val components = spec().getAsJsonObject("components")
        val references = REF.findAll(RepositoryApiDocs.toJson()).map { it.groupValues[1] to it.groupValues[2] }.toSet()
        assertTrue(references.size > 20, "poucas referências: ${references.size}")
        val broken = references.filterNot { (kind, name) -> components.getAsJsonObject(kind).has(name) }
        assertEquals(emptyList<Pair<String, String>>(), broken)
    }

    @Test
    fun criteriaFieldsAreTheOnesOfTheDomain() {
        // Confere contra criterions(), e não contra uma lista escrita aqui: lista fixa envelheceria junto com a
        // documentação, e as duas passariam a concordar sobre o que já não é verdade.
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

        // items é array na resposta e envelope na projeção — descrever só uma das formas induziria ao erro
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
            assertTrue(paths.has(route), "falta documentar $route")
        }

        env.loginAs("admin")
        val token = env.authClient!!.accessToken
        fun served(response: HttpResponse<String>) =
            // 404 com corpo de erro da aplicação é resposta da rota ("não achei a linha"); o do Javalin é "não há rota"
            response.statusCode() != 405 && !(response.statusCode() == 404 && "Endpoint" in response.body())
        // a conferência distingue a rota (ou o método) que não existe
        for ((method, path) in listOf("POST" to "/api/repo/product/no-such-operation", "GET" to "/api/repo/product/fetch/x", "PUT" to "/api/tx/begin")) {
            assertFalse(served(send(method, path, if (method == "GET") null else "{}", token)), "$method $path")
        }

        val notServed = ArrayList<String>()
        var openTx: String? = null
        // o begin fica por último: com transação aberta, as escritas sem X-Tx-Id seriam recusadas
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
        assertNotNull(openTx, "begin não abriu transação")
        val rollback = HttpRequest.newBuilder(URI("http://localhost:${env.port}/api/tx/rollback"))
            .header("Authorization", "Bearer $token").header("X-Tx-Id", openTx).POST(HttpRequest.BodyPublishers.ofString("{}")).build()
        assertEquals(200, HttpClient.newHttpClient().send(rollback, HttpResponse.BodyHandlers.ofString()).statusCode())

        assertEquals(emptyList<String>(), notServed)
    }

    @Test
    fun descriptionsAreFreeOfPlaceholders() {
        val json = RepositoryApiDocs.toJson()
        assertFalse("TODO" in json)
        assertFalse("FIXME" in json)
    }
}
