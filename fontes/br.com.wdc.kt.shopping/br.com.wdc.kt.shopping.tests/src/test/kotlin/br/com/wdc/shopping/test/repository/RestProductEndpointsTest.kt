package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.exception.BusinessException
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * O contrato HTTP de produto visto de fora, sem passar pelo repositório do cliente: o que um consumidor
 * externo (ou um cliente de versão anterior) envia e recebe.
 */
class RestProductEndpointsTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-shopping-rest-product-endpoints")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private fun post(operation: String, body: String) = env.transport.postJson("/api/repo/product/$operation", body)

    @Test
    fun looseValueCriterion_fromOlderClients_stillMeansEquality() {
        val response = post("fetch", """{"productId":${DBReset.BOLA_WILSON_ID},"projection":{"id":1,"name":"~"}}""")
        assertEquals("""{"items":[{"id":${DBReset.BOLA_WILSON_ID},"name":"Bola Wilson"}]}""", response)
    }

    @Test
    fun expressiveCriterion_inTheWireFormat() {
        val response = post(
            "fetch",
            """{"price":{"or":true,"p":[{"o":"LT","v":[10]},{"o":"GT","v":[100]}]},"orderBy":"CHEAPEST_FIRST","projection":{"name":"~"}}""",
        )
        assertEquals(
            """{"items":[{"id":${DBReset.FITA_VEDA_ROSCA_ID},"name":"Fita veda rosca"},{"id":${DBReset.CAFETEIRA_ID},"name":"Cafeteira design italiano"}]}""",
            response,
        )
    }

    @Test
    fun unknownOperator_isDiscarded_andUnknownFieldsAreIgnored() {
        val response = post("count", """{"price":{"p":[{"o":"REGEXP","v":[1]},{"o":"LT","v":[10]}]},"fromTheFuture":{"a":[1]}}""")
        assertEquals("""{"count":1}""", response)
    }

    @Test
    fun malformedCriterion_isRefused_andDeleteRemovesNothing() {
        val before = post("count", "{}")
        // operador e valores em desacordo: contava como filtro informado e sumia do SQL (DELETE sem WHERE)
        for (criterion in listOf(
            """{"price":{"p":[{"o":"BETWEEN","v":[1]}]}}""",
            """{"price":{"p":[{"o":"EQ"}]}}""",
            """{"price":{"p":[{"o":"GT","v":[1,2]}]}}""",
            """{"name":{"p":[{"o":"LIKE","v":[null]}]}}""",
        )) {
            val e = assertThrows(BusinessException::class.java, { post("delete", criterion) }, criterion)
            assertTrue(e.message!!.contains("critério"), e.message)
            assertThrows(BusinessException::class.java, { post("fetch", criterion) }, criterion)
        }
        assertEquals(before, post("count", "{}"), "nenhum produto apagado")
    }

    @Test
    fun fetch_withoutProjection_bringsEverythingButTheImage() {
        val response = post("fetch", """{"productId":${DBReset.PEN_DRIVE2GB_ID}}""")
        assertTrue(response.startsWith("""{"items":[{"id":${DBReset.PEN_DRIVE2GB_ID},"name":"Pen Drive 2GB","price":16"""), response)
        assertTrue(""""description":""" in response, response)
        assertFalse(""""image"""" in response, response)
    }

    @Test
    fun fetchPage_returnsItemsAndTotal() {
        val response = post("fetch-page", """{"orderBy":"OLDEST_FIRST","page":1,"pageSize":3,"projection":{"id":1}}""")
        assertEquals("""{"items":[{"id":${DBReset.PEN_DRIVE2GB_ID}}],"totalItems":4}""", response)
    }

    @Test
    fun fetchById_byGetAndByPost() {
        val byGet = env.transport.getJson("/api/repo/product/${DBReset.FITA_VEDA_ROSCA_ID}")
        assertTrue(byGet.startsWith("""{"id":${DBReset.FITA_VEDA_ROSCA_ID},"name":"Fita veda rosca","price":2.67"""), byGet)

        val byPost = post("fetch-by-id", """{"id":${DBReset.FITA_VEDA_ROSCA_ID},"projection":{"name":"~"}}""")
        assertEquals("""{"id":${DBReset.FITA_VEDA_ROSCA_ID},"name":"Fita veda rosca"}""", byPost)

        assertNull(env.transport.postJsonNullable("/api/repo/product/fetch-by-id", """{"id":${Long.MAX_VALUE}}"""))
    }

    @Test
    fun insert_returnsTheGeneratedId_andUpdateTouchesOnlyThePresentKeys() {
        // a entidade solta, como antes da 0.4.0
        val inserted = post("insert", """{"name":"Teclado","price":80.5,"description":"d"}""")
        val id = Regex(""""id":(\d+)""").find(inserted)!!.groupValues[1]
        assertEquals("""{"success":true,"id":$id,"entity":{"id":$id,"name":"Teclado","price":80.5,"description":"d"}}""", inserted)

        assertEquals("""{"success":true}""", post("update", """{"id":$id,"name":"Teclado 2"}"""))
        assertEquals(
            """{"id":$id,"name":"Teclado 2","price":80.5,"description":"d"}""",
            post("fetch-by-id", """{"id":$id}"""),
        )
        assertEquals("""{"count":1}""", post("delete", """{"productId":$id}"""))
    }

    @Test
    fun insert_inTheEnvelope_withProjection() {
        val inserted = post(
            "insert",
            """{"entity":{"name":"Envelope","price":1.5,"description":"d"},"projection":{"name":"~","price":1.5,"description":"~"}}""",
        )
        val id = Regex(""""id":(\d+)""").find(inserted)!!.groupValues[1]
        assertTrue(inserted.startsWith("""{"success":true,"id":$id,"entity":{"id":$id,"name":"Envelope""""), inserted)
        post("delete", """{"productId":$id}""")
    }

    @Test
    fun insertedUser_comesBackWithoutThePassword() {
        val inserted = env.transport.postJson(
            "/api/repo/user/insert",
            """{"entity":{"userName":"semsenha","password":"0123456789abcdef0123456789abcdef","name":"Sem Senha","roles":"CUSTOMER"}}""",
        )
        assertTrue(""""success":true""" in inserted, inserted)
        assertTrue(""""userName":"semsenha"""" in inserted, inserted)
        assertFalse("password" in inserted, inserted)
        val id = Regex(""""id":(\d+)""").find(inserted)!!.groupValues[1]
        env.transport.postJson("/api/repo/user/delete", """{"userId":$id}""")
    }

    @Test
    fun removedRoutes_noLongerExist() {
        for (old in listOf("upsert", "fetchPage", "fetchById")) {
            assertNull(env.transport.postJsonNullable("/api/repo/product/$old", "{}"), old)
        }
    }
}
