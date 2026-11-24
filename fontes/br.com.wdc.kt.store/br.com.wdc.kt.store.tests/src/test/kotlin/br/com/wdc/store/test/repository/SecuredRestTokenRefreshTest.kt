package br.com.wdc.store.test.repository

import br.com.wdc.store.domain.exception.BusinessException
import br.com.wdc.store.domain.product.Product
import br.com.wdc.store.domain.product.ProductCriteria
import br.com.wdc.store.scripts.sgbd.DBReset
import br.com.wdc.store.test.util.RestTestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * What the transport does when the server refuses the access token (401): renews the session and repeats the call
 * once; with no way to renew, it notifies the application and lets the refusal propagate.
 */
class SecuredRestTokenRefreshTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-store-rest-token-refresh", jwtSecret = "test-secret-with-enough-length-0123456789")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    @AfterEach
    fun signOut() = env.logout()

    /**
     * Makes the client present a token the server does not accept, until the session is renewed — the effect of an
     * expired access token.
     *
     * @return how many times the renewal was requested
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
        // with the new token, the following calls do not renew again
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
        assertTrue(env.productRepo.updateImage(DBReset.COFFEE_MAKER_ID, byteArrayOf(1, 2, 3)))
        assertEquals(1, refreshes())

        refreshes = expireAccessToken()
        val product = Product().apply { name = "after renewing"; price = 1.5; description = "d" }
        assertTrue(env.productRepo.insert(product))
        assertEquals(1, refreshes())
        // the repeated write did not duplicate
        assertEquals(1, env.productRepo.count(ProductCriteria().withName("after renewing")))
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
