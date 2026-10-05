package br.com.wdc.shopping.test.repository

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.domain.exception.BusinessException
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Produto pelo caminho REST **com a segurança ligada**: autenticação por JWT e o controle de acesso dos
 * decoradores `Secured*`, atravessando cliente HTTP, filtro, controller e repositório.
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

    private fun newProduct() = Product().apply { name = "Teclado"; price = 80.0; description = "d" }

    private fun assertHttp(status: Int, block: suspend () -> Unit) {
        val e = assertThrows(BusinessException::class.java) { runBlocking { block() } }
        assertTrue(e.message!!.startsWith("HTTP $status"), "esperava HTTP $status, veio: ${e.message}")
    }

    // :: Autenticação

    @Test
    fun withoutAuthentication_everyRepositoryOperationIsRefused() {
        assertHttp(401) { repo().fetch(ProductCriteria()) }
        assertHttp(401) { repo().count(ProductCriteria()) }
        assertHttp(401) { repo().insert(newProduct()) }
        assertHttp(401) { repo().delete(ProductCriteria().withProductId(DBReset.PEN_DRIVE2GB_ID)) }
    }

    @Test
    fun wrongPassword_doesNotAuthenticate() {
        assertThrows(BusinessException::class.java) { env.loginAs("admin", "senha-errada") }
        assertHttp(401) { repo().fetch(ProductCriteria()) }
    }

    // :: Controle de acesso

    @Test
    fun admin_readsAndWrites() = runBlocking {
        env.loginAs("admin")

        assertEquals(4, repo().fetch(ProductCriteria()).size)
        assertEquals("Bola Wilson", repo().fetchById(DBReset.BOLA_WILSON_ID)!!.name)
        assertEquals(4, repo().fetchPage(ProductCriteria(), 0, 10).totalItems)

        val product = newProduct()
        assertTrue(repo().insert(product))
        assertTrue(repo().update(Product().apply { id = product.id; name = "Teclado 2" }, null, Product().apply { name = ProjectionValues.str }))
        assertEquals("Teclado 2", repo().fetchById(product.id!!)!!.name)
        assertEquals(1, repo().delete(ProductCriteria().withProductId(product.id)))
    }

    @Test
    fun customer_reads_butCannotWrite() = runBlocking {
        env.loginAs("fulano")

        assertEquals(4, repo().count(ProductCriteria()))
        assertEquals(4, repo().fetch(ProductCriteria()).size)
        assertNotNull(repo().fetchById(DBReset.CAFETEIRA_ID))

        assertHttp(403) { repo().insert(newProduct()) }
        assertHttp(403) { repo().update(Product().apply { id = DBReset.CAFETEIRA_ID; name = "x" }, null, Product().apply { name = ProjectionValues.str }) }
        assertHttp(403) { repo().delete(ProductCriteria().withProductId(DBReset.CAFETEIRA_ID)) }
        // nada mudou
        assertEquals("Cafeteira design italiano", repo().fetchById(DBReset.CAFETEIRA_ID)!!.name)
        assertEquals(4, repo().count(ProductCriteria()))
    }

    // :: Imagem

    @Test
    fun image_isPublicToRead_butNotToWrite() = runBlocking {
        val original = repo().fetchImage(DBReset.CAFETEIRA_ID)
        assertNotNull(original)

        val replacement = ByteArray(64) { it.toByte() }
        assertHttp(401) { repo().updateImage(DBReset.CAFETEIRA_ID, replacement) }

        env.loginAs("fulano")
        assertThrows(BusinessException::class.java) { runBlocking { repo().updateImage(DBReset.CAFETEIRA_ID, replacement) } }
        assertArrayEquals(original, repo().fetchImage(DBReset.CAFETEIRA_ID))

        env.loginAs("admin")
        assertTrue(repo().updateImage(DBReset.CAFETEIRA_ID, replacement))
    }

    // :: Pedidos recusados

    @Test
    fun unknownOrderBy_is400_namingTheValueAndTheAcceptedOnes() {
        env.loginAs("admin")
        val e = assertThrows(BusinessException::class.java) {
            env.transport.postJson("/api/repo/product/fetch", """{"orderBy":"RANDOM"}""")
        }
        assertTrue(e.message!!.startsWith("HTTP 400"), e.message)
        assertTrue("ordenação desconhecida" in e.message!! && "RANDOM" in e.message!!, e.message)
        assertTrue("OLDEST_FIRST, NEWEST_FIRST, NAME_A_TO_Z, CHEAPEST_FIRST, MOST_EXPENSIVE_FIRST" in e.message!!, e.message)
    }

    @Test
    fun deleteWithEmptyCriteria_is400() {
        env.loginAs("admin")
        assertHttp(400) { repo().delete(ProductCriteria()) }
        assertEquals(4, runBlocking { repo().count(ProductCriteria()) })
    }
}
