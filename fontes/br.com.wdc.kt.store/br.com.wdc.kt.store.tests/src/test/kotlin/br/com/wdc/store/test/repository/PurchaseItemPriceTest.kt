package br.com.wdc.store.test.repository

import br.com.wdc.store.domain.exception.BusinessException
import br.com.wdc.store.domain.product.Product
import br.com.wdc.store.domain.purchase.Purchase
import br.com.wdc.store.domain.purchaseitem.PurchaseItem
import br.com.wdc.store.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.store.domain.user.User
import br.com.wdc.store.scripts.sgbd.DBReset
import br.com.wdc.store.test.util.TestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * The price of a purchase item is the server's on every path: in process too (the checkout of the remote React
 * view and the desktop), the repository reads it from the product right before the INSERT, whatever the caller sent.
 */
class PurchaseItemPriceTest {

    companion object {
        private val env = TestEnvironment("wedocode-store-item-price")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)

        private const val WILSON_BALL_PRICE = 45.30
        private const val FLASH_DRIVE_PRICE = 16.0
    }

    private fun item(productId: Long, price: Double?) = PurchaseItem().apply {
        product = Product().apply { id = productId }
        amount = 1
        this.price = price
    }

    @Test
    fun purchaseInsertedInProcess_takesTheProductsPrices_notTheOnesSent() = runBlocking {
        val purchase = Purchase().apply {
            buyDate = Clock.System.now()
            user = User().apply { id = DBReset.FULANO_ID }
            items = mutableListOf(item(DBReset.WILSON_BALL_ID, 0.01), item(DBReset.FLASH_DRIVE_2GB_ID, null))
        }
        assertTrue(env.purchaseRepo.insert(purchase))
        val prices = env.purchaseItemRepo.fetch(PurchaseItemCriteria().withPurchaseId(purchase.id)).map { it.productId to it.price }.toSet()
        assertEquals(setOf(DBReset.WILSON_BALL_ID to WILSON_BALL_PRICE, DBReset.FLASH_DRIVE_2GB_ID to FLASH_DRIVE_PRICE), prices)
    }

    @Test
    fun anItemOfAProductThatDoesNotExist_isRefused() = runBlocking {
        val purchase = Purchase().apply {
            buyDate = Clock.System.now()
            user = User().apply { id = DBReset.FULANO_ID }
            items = mutableListOf(item(987_654L, 1.0))
        }
        val e = assertThrows(BusinessException::class.java) { runBlocking { env.purchaseRepo.insert(purchase) } }
        assertTrue("987654" in e.message!!, e.message)
    }
}
