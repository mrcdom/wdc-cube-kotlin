package br.com.wdc.store.test.repository

import br.com.wdc.framework.rest.rpc.RepositoryRpc
import br.com.wdc.store.domain.exception.BusinessException
import br.com.wdc.store.domain.product.Product
import br.com.wdc.store.domain.product.ProductCriteria
import br.com.wdc.store.domain.product.ProductRepository
import br.com.wdc.store.scripts.sgbd.DBReset
import br.com.wdc.store.test.util.RestTestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * The product HTTP contract seen from outside, without going through the client repository: what an external
 * consumer (or a client of an earlier version) sends and receives.
 */
class RestProductEndpointsTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-store-rest-product-endpoints")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private fun post(operation: String, body: String) = env.postJson("/api/repo/product/$operation", body)

    @Test
    fun looseValueCriterion_fromOlderClients_stillMeansEquality() {
        val response = post("fetch", """{"productId":${DBReset.WILSON_BALL_ID},"projection":{"id":1,"name":"~"}}""")
        assertEquals("""{"items":[{"id":${DBReset.WILSON_BALL_ID},"name":"Wilson Ball"}]}""", response)
    }

    @Test
    fun expressiveCriterion_inTheWireFormat() {
        val response = post(
            "fetch",
            """{"price":{"or":true,"p":[{"o":"LT","v":[10]},{"o":"GT","v":[100]}]},"orderBy":"CHEAPEST_FIRST","projection":{"name":"~"}}""",
        )
        assertEquals(
            """{"items":[{"id":${DBReset.THREAD_SEAL_TAPE_ID},"name":"Thread Seal Tape"},{"id":${DBReset.COFFEE_MAKER_ID},"name":"Italian Design Coffee Maker"}]}""",
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
        // operator and values in disagreement: it counted as a provided filter and vanished from the SQL (DELETE without WHERE)
        for (criterion in listOf(
            """{"price":{"p":[{"o":"BETWEEN","v":[1]}]}}""",
            """{"price":{"p":[{"o":"EQ"}]}}""",
            """{"price":{"p":[{"o":"GT","v":[1,2]}]}}""",
            """{"name":{"p":[{"o":"LIKE","v":[null]}]}}""",
            // "" for a number read as null: NE [""] became IS NOT NULL and the delete wiped the table
            """{"productId":{"p":[{"o":"NE","v":[""]}]}}""",
            """{"price":{"p":[{"o":"EQ","v":[""]}]}}""",
            // LIKE on a number: the translator failed on a cast (500)
            """{"price":{"p":[{"o":"LIKE","v":[1]}]}}""",
        )) {
            val e = assertThrows(BusinessException::class.java, { post("delete", criterion) }, criterion)
            assertTrue(e.message!!.contains("criterion"), e.message)
            assertThrows(BusinessException::class.java, { post("fetch", criterion) }, criterion)
        }
        assertEquals(before, post("count", "{}"), "no product deleted")
    }

    @Test
    fun fetch_withoutProjection_bringsEverythingButTheImage() {
        val response = post("fetch", """{"productId":${DBReset.FLASH_DRIVE_2GB_ID}}""")
        assertTrue(response.startsWith("""{"items":[{"id":${DBReset.FLASH_DRIVE_2GB_ID},"name":"USB Flash Drive 2GB","price":16"""), response)
        assertTrue(""""description":""" in response, response)
        assertFalse(""""image"""" in response, response)
    }

    @Test
    fun sliceNumbersSentAsText_areRefused_notConverted() {
        // a value of the wrong type is refused, not interpreted (as in the C# version)
        for ((operation, body) in listOf(
            "fetch" to """{"limit":"10"}""",
            "fetch" to """{"offset":"0","limit":2}""",
            "fetch" to """{"limit":2.5}""",
            "fetch-page" to """{"page":"0","pageSize":2}""",
            "fetch-page" to """{"page":0,"pageSize":true}""",
        )) {
            val e = assertThrows(BusinessException::class.java, { post(operation, body) }, body)
            assertTrue(e.message!!.startsWith("HTTP 400") && "must be an integer" in e.message!!, e.message)
        }
        // the JSON null still means "not given"
        assertEquals(post("count", "{}"), post("count", """{"limit":null,"offset":null}"""))
    }

    @Test
    fun limitOrPageSizeAboveTheCap_isRefused() {
        val over = RepositoryRpc.DEFAULT_MAX_LIMIT + 1
        for ((operation, body) in listOf(
            "fetch" to """{"limit":$over}""",
            "fetch-page" to """{"page":0,"pageSize":$over}""",
        )) {
            val e = assertThrows(BusinessException::class.java, { post(operation, body) }, operation)
            assertTrue(e.message!!.startsWith("HTTP 400"), e.message)
        }
        assertTrue(post("fetch", """{"limit":${RepositoryRpc.DEFAULT_MAX_LIMIT},"projection":{"id":1}}""").startsWith("""{"items":["""))
    }

    @Test
    fun fetchWithoutLimit_overTheCap_isRefused_insteadOfCutShort() = runBlocking {
        // the server-side (JDBC) repository: the seed has 4 products, this takes them just past the cap
        val serverRepo = ProductRepository.BEAN.get()
        val missing = RepositoryRpc.DEFAULT_MAX_LIMIT + 1 - serverRepo.count(ProductCriteria()).toInt()
        repeat(missing) { serverRepo.insert(Product().apply { name = "bulk $it"; price = 1.5; description = "bulk" }) }

        val e = assertThrows(BusinessException::class.java) { post("fetch", """{"projection":{"id":1}}""") }
        assertTrue(e.message!!.startsWith("HTTP 400") && "limit" in e.message!!, e.message)
        // paging through still works, and so does counting
        val firstPage = post("fetch", """{"limit":${RepositoryRpc.DEFAULT_MAX_LIMIT},"projection":{"id":1}}""")
        assertEquals(RepositoryRpc.DEFAULT_MAX_LIMIT, Regex("\"id\"").findAll(firstPage).count())
        assertEquals("""{"count":${RepositoryRpc.DEFAULT_MAX_LIMIT + 1}}""", post("count", "{}"))
    }

    @Test
    fun fetchPage_returnsItemsAndTotal() {
        val response = post("fetch-page", """{"orderBy":"OLDEST_FIRST","page":1,"pageSize":3,"projection":{"id":1}}""")
        assertEquals("""{"items":[{"id":${DBReset.FLASH_DRIVE_2GB_ID}}],"totalItems":4}""", response)
    }

    @Test
    fun fetchById_byGetAndByPost() {
        val byGet = env.getJsonPublic("/api/repo/product/${DBReset.THREAD_SEAL_TAPE_ID}")
        assertTrue(byGet.startsWith("""{"id":${DBReset.THREAD_SEAL_TAPE_ID},"name":"Thread Seal Tape","price":2.67"""), byGet)

        val byPost = post("fetch-by-id", """{"id":${DBReset.THREAD_SEAL_TAPE_ID},"projection":{"name":"~"}}""")
        assertEquals("""{"id":${DBReset.THREAD_SEAL_TAPE_ID},"name":"Thread Seal Tape"}""", byPost)

        assertNull(env.postJsonNullable("/api/repo/product/fetch-by-id", """{"id":${Long.MAX_VALUE}}"""))
    }

    @Test
    fun insert_returnsTheGeneratedId_andUpdateTouchesOnlyThePresentKeys() {
        // the bare entity, as before 0.4.0
        val inserted = post("insert", """{"name":"Keyboard","price":80.5,"description":"d"}""")
        val id = Regex(""""id":(\d+)""").find(inserted)!!.groupValues[1]
        assertEquals("""{"success":true,"id":$id,"entity":{"id":$id,"name":"Keyboard","price":80.5,"description":"d"}}""", inserted)

        assertEquals("""{"success":true}""", post("update", """{"id":$id,"name":"Keyboard 2"}"""))
        assertEquals(
            """{"id":$id,"name":"Keyboard 2","price":80.5,"description":"d"}""",
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
        val inserted = env.postJson(
            "/api/repo/user/insert",
            """{"entity":{"userName":"nosecret","password":"0123456789abcdef0123456789abcdef","name":"No Secret","roles":"CUSTOMER"}}""",
        )
        assertTrue(""""success":true""" in inserted, inserted)
        assertTrue(""""userName":"nosecret"""" in inserted, inserted)
        assertFalse("password" in inserted, inserted)
        val id = Regex(""""id":(\d+)""").find(inserted)!!.groupValues[1]
        env.postJson("/api/repo/user/delete", """{"userId":$id}""")
    }

    @Test
    fun removedRoutes_noLongerExist() {
        for (old in listOf("upsert", "fetchPage", "fetchById")) {
            assertNull(env.postJsonNullable("/api/repo/product/$old", "{}"), old)
        }
    }
}
