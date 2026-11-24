package br.com.wdc.shopping.test.transaction

import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.shopping.domain.exception.BusinessException
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.persistence.client.RestTransactionService
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** The remote transaction with security on: it requires authentication, and the owner is the user. */
class SecuredRemoteTransactionRestTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-shopping-rest-secured-remote-tx", jwtSecret = "test-secret-with-enough-length-0123456789")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    @AfterEach
    fun signOut() = env.logout()

    private fun item(productId: Long, purchase: Purchase) = PurchaseItem().apply {
        this.purchase = purchase
        product = Product().apply { id = productId }
        amount = 1
        // no price: a customer does not send it — the repository reads the product's in the INSERT
    }

    /** A purchase of fulano's, made by himself: a customer buys only for themselves. */
    private fun fulanosPurchase() = Purchase().apply {
        buyDate = Clock.System.now()
        user = User().apply { id = DBReset.FULANO_ID }
    }

    @Test
    fun withoutAuthentication_theTransactionEndpointsAreRefused() {
        val e = assertThrows(BusinessException::class.java) { env.transport.postJson("/api/tx/begin", "{}") }
        assertTrue(e.message!!.startsWith("HTTP 401"), e.message)
    }

    @Test
    fun customer_buysInOneTransaction_underTheirOwnScope() = runBlocking {
        env.loginAs("fulano")
        val purchase = fulanosPurchase()
        RestTransactionService(env.transport).required {
            assertTrue(env.purchaseRepo.insert(purchase))
            // the check that the purchase belongs to the user sees the purchase opened in this same transaction
            assertTrue(env.purchaseItemRepo.insert(item(DBReset.COFFEE_MAKER_ID, purchase)))
            assertTrue(env.purchaseItemRepo.insert(item(DBReset.FLASH_DRIVE_2GB_ID, purchase)))
        }
        assertEquals(DBReset.FULANO_ID, env.purchaseRepo.fetchById(purchase.id!!)!!.userId)
        assertEquals(2, env.purchaseItemRepo.count(PurchaseItemCriteria().withPurchaseId(purchase.id)))
    }

    @Test
    fun refusalInTheMiddle_undoesWhatTheSameTransactionWrote() = runBlocking {
        env.loginAs("fulano")
        assertThrows(AccessDeniedException::class.java) {
            runBlocking {
                RestTransactionService(env.transport).required {
                    val purchase = fulanosPurchase()
                    assertTrue(env.purchaseRepo.insert(purchase))
                    // item in an admin purchase: 403
                    env.purchaseItemRepo.insert(item(DBReset.COFFEE_MAKER_ID, Purchase().apply { id = DBReset.ADMIN_FIRST_PURCHASE_ID }))
                }
            }
        }
        assertEquals(0, env.purchaseRepo.count(PurchaseCriteria()))

        env.loginAs("admin")
        assertEquals(2, env.purchaseRepo.count(PurchaseCriteria()))
        assertEquals(3, env.purchaseItemRepo.count(PurchaseItemCriteria()))
    }

    @Test
    fun transactionOfOneUser_cannotBeUsedByAnother() = runBlocking {
        env.loginAs("fulano")
        val txId = Regex("\"txId\":\"([^\"]+)\"").find(env.transport.postJson("/api/tx/begin", "{}"))!!.groupValues[1]
        env.transport.transactionIdSupplier = { txId }
        try {
            env.loginAs("beotrano")
            assertThrows(AccessDeniedException::class.java) { env.transport.postJson("/api/tx/commit", "{}") }
            assertThrows(AccessDeniedException::class.java) {
                runBlocking { env.purchaseRepo.insert(Purchase().apply { buyDate = Clock.System.now() }) }
            }
            env.loginAs("fulano")
            assertTrue("rolledback" in env.transport.postJson("/api/tx/rollback", "{}"))
        } finally {
            env.transport.transactionIdSupplier = null
        }
    }
}
