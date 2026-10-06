package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.exception.BusinessException
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * O que o transporte faz quando o servidor recusa o token de acesso (401): renova a sessão e repete a chamada
 * uma vez; sem como renovar, avisa a aplicação e deixa a recusa subir.
 */
class SecuredRestTokenRefreshTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-shopping-rest-token-refresh", jwtSecret = "test-secret-with-enough-length-0123456789")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    @AfterEach
    fun signOut() = env.logout()

    /**
     * Faz o cliente apresentar um token que o servidor não aceita, até a sessão ser renovada — o efeito de um
     * token de acesso vencido.
     *
     * @return quantas vezes a renovação foi pedida
     */
    private fun expireAccessToken(): () -> Int {
        val renew = env.transport.refreshHandler!!
        var stale = true
        var refreshes = 0
        env.transport.accessTokenSupplier = { if (stale) "token.vencido.invalido" else env.authClient!!.accessToken }
        env.transport.refreshHandler = {
            refreshes++
            renew().also { renewed -> if (renewed) stale = false }
        }
        return { refreshes }
    }

    @Test
    fun expiredToken_isRenewed_andTheCallIsRepeated() = runBlocking {
        env.loginAs("admin")
        val firstToken = env.authClient!!.accessToken
        val refreshes = expireAccessToken()

        assertEquals(4, env.productRepo.count(ProductCriteria()))

        assertEquals(1, refreshes())
        assertNotEquals(firstToken, env.authClient!!.accessToken)
        // com o token novo, as chamadas seguintes não renovam de novo
        assertEquals(4, env.productRepo.fetch(ProductCriteria()).size)
        assertEquals(1, refreshes())
    }

    @Test
    fun everyAuthenticatedCall_renews_includingBytes() = runBlocking {
        env.loginAs("admin")

        var refreshes = expireAccessToken()
        assertNull(env.productRepo.fetchById(987_654L))
        assertEquals(1, refreshes())

        refreshes = expireAccessToken()
        assertTrue(env.productRepo.updateImage(DBReset.CAFETEIRA_ID, byteArrayOf(1, 2, 3)))
        assertEquals(1, refreshes())

        refreshes = expireAccessToken()
        val product = Product().apply { name = "depois de renovar"; price = 1.5; description = "d" }
        assertTrue(env.productRepo.insert(product))
        assertEquals(1, refreshes())
        // a escrita repetida não duplicou
        assertEquals(1, env.productRepo.count(ProductCriteria().withName("depois de renovar")))
    }

    @Test
    fun whenTheSessionCannotBeRenewed_theApplicationIsTold_andTheRefusalRises() {
        env.loginAs("admin")
        var failures = 0
        var refreshes = 0
        env.transport.accessTokenSupplier = { "token.vencido.invalido" }
        env.transport.refreshHandler = { refreshes++; false }
        env.transport.onAuthFailure = { failures++ }

        val e = assertThrows(BusinessException::class.java) { runBlocking { env.productRepo.count(ProductCriteria()) } }

        assertTrue(e.message!!.startsWith("HTTP 401"), e.message)
        assertEquals(1, refreshes)
        assertEquals(1, failures)
    }

    @Test
    fun renewedTokenThatIsAlsoRefused_doesNotLoop() {
        env.loginAs("admin")
        var refreshes = 0
        env.transport.accessTokenSupplier = { "token.vencido.invalido" }
        env.transport.refreshHandler = { refreshes++; true }

        val e = assertThrows(BusinessException::class.java) { runBlocking { env.productRepo.count(ProductCriteria()) } }

        assertTrue(e.message!!.startsWith("HTTP 401"), e.message)
        assertEquals(1, refreshes)
    }

    @Test
    fun withoutAToken_a401IsNotARenewal() {
        var refreshes = 0
        env.transport.refreshHandler = { refreshes++; true }
        try {
            val e = assertThrows(BusinessException::class.java) { runBlocking { env.productRepo.count(ProductCriteria()) } }
            assertTrue(e.message!!.startsWith("HTTP 401"), e.message)
            assertEquals(0, refreshes)
        } finally {
            env.transport.refreshHandler = null
        }
    }
}
