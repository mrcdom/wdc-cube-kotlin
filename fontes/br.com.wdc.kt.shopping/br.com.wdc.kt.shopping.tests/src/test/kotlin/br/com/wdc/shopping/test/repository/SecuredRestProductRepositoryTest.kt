package br.com.wdc.shopping.test.repository

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.domain.exception.BusinessException
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Product over the REST path **with security on**: JWT authentication and the product data policy, going through
 * HTTP client, route pipeline, controller and repository.
 */
class SecuredRestProductRepositoryTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-shopping-rest-secured-product", jwtSecret = "test-secret-with-enough-length-0123456789")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private fun repo(): ProductRepository = env.productRepo

    @AfterEach
    fun signOut() = env.logout()

    private fun newProduct() = Product().apply { name = "Keyboard"; price = 80.0; description = "d" }

    private fun assertHttp(status: Int, block: suspend () -> Unit) {
        val e = assertThrows(RuntimeException::class.java) { runBlocking { block() } }
        assertTrue(e.message!!.startsWith("HTTP $status"), "expected HTTP $status, got: ${e.message}")
    }

    // :: Authentication

    @Test
    fun withoutAuthentication_everyRepositoryOperationIsRefused() {
        assertHttp(401) { repo().fetch(ProductCriteria()) }
        assertHttp(401) { repo().count(ProductCriteria()) }
        assertHttp(401) { repo().insert(newProduct()) }
        assertHttp(401) { repo().delete(ProductCriteria().withProductId(DBReset.FLASH_DRIVE_2GB_ID)) }
    }

    @Test
    fun wrongPassword_doesNotAuthenticate() {
        assertThrows(BusinessException::class.java) { env.loginAs("admin", "wrong-password") }
        assertHttp(401) { repo().fetch(ProductCriteria()) }
    }

    // :: Access control

    @Test
    fun admin_readsAndWrites() = runBlocking {
        env.loginAs("admin")

        assertEquals(4, repo().fetch(ProductCriteria()).size)
        assertEquals("Wilson Ball", repo().fetchById(DBReset.WILSON_BALL_ID)!!.name)
        assertEquals(4, repo().fetchPage(ProductCriteria(), 0, 10).totalItems)

        val product = newProduct()
        assertTrue(repo().insert(product))
        assertTrue(repo().update(Product().apply { id = product.id; name = "Keyboard 2" }, null, Product().apply { name = ProjectionValues.str }))
        assertEquals("Keyboard 2", repo().fetchById(product.id!!)!!.name)
        assertEquals(1, repo().delete(ProductCriteria().withProductId(product.id)))
    }

    @Test
    fun customer_reads_butCannotWrite() = runBlocking {
        env.loginAs("fulano")

        assertEquals(4, repo().count(ProductCriteria()))
        assertEquals(4, repo().fetch(ProductCriteria()).size)
        assertNotNull(repo().fetchById(DBReset.COFFEE_MAKER_ID))

        assertHttp(403) { repo().insert(newProduct()) }
        assertHttp(403) { repo().update(Product().apply { id = DBReset.COFFEE_MAKER_ID; name = "x" }, null, Product().apply { name = ProjectionValues.str }) }
        assertHttp(403) { repo().delete(ProductCriteria().withProductId(DBReset.COFFEE_MAKER_ID)) }
        // nothing changed
        assertEquals("Italian Design Coffee Maker", repo().fetchById(DBReset.COFFEE_MAKER_ID)!!.name)
        assertEquals(4, repo().count(ProductCriteria()))
    }

    @Test
    fun manager_writesAndDeletesProducts() = runBlocking {
        env.loginAs("admin")
        val manager = User().apply { userName = "manager"; password = env.authClient!!.encryptPassword("m4nager"); name = "Mary Major"; roles = "MANAGER" }
        assertTrue(env.userRepo.insert(manager))
        env.loginAs("manager", "m4nager")

        val product = newProduct()
        assertTrue(repo().insert(product))
        assertTrue(repo().update(Product().apply { id = product.id; price = 75.5 }, null, Product().apply { price = ProjectionValues.f64 }))
        assertEquals(75.5, repo().fetchById(product.id!!)!!.price)
        assertTrue(repo().updateImage(product.id!!, ByteArray(16) { it.toByte() }))
        assertEquals(1, repo().delete(ProductCriteria().withProductId(product.id)))
        assertEquals(4, repo().count(ProductCriteria()))
    }

    // :: Image

    @Test
    fun image_isPublicToRead_butNotToWrite() = runBlocking {
        val original = repo().fetchImage(DBReset.COFFEE_MAKER_ID)
        assertNotNull(original)

        val replacement = ByteArray(64) { it.toByte() }
        assertHttp(401) { repo().updateImage(DBReset.COFFEE_MAKER_ID, replacement) }

        env.loginAs("fulano")
        // refused by the policy, as in the other writes (it used to come out as a generic upload error)
        assertHttp(403) { repo().updateImage(DBReset.COFFEE_MAKER_ID, replacement) }
        assertArrayEquals(original, repo().fetchImage(DBReset.COFFEE_MAKER_ID))

        env.loginAs("admin")
        assertTrue(repo().updateImage(DBReset.COFFEE_MAKER_ID, replacement))
    }

    // :: Refused requests

    @Test
    fun unknownOrderBy_is400_namingTheValueAndTheAcceptedOnes() {
        env.loginAs("admin")
        val e = assertThrows(BusinessException::class.java) {
            env.transport.postJson("/api/repo/product/fetch", """{"orderBy":"RANDOM"}""")
        }
        assertTrue(e.message!!.startsWith("HTTP 400"), e.message)
        assertTrue("unknown ordering" in e.message!! && "RANDOM" in e.message!!, e.message)
        assertTrue("OLDEST_FIRST, NEWEST_FIRST, NAME_A_TO_Z, CHEAPEST_FIRST, MOST_EXPENSIVE_FIRST" in e.message!!, e.message)
    }

    @Test
    fun deleteWithEmptyCriteria_is400() {
        env.loginAs("admin")
        assertHttp(400) { repo().delete(ProductCriteria()) }
        assertEquals(4, runBlocking { repo().count(ProductCriteria()) })
    }
}
