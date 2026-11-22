package br.com.wdc.shopping.test.repository

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.domain.exception.BusinessException
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** The price of a purchase item is the server's: written through the API, it is the product's current price. */
class RestPurchaseItemPriceTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-shopping-rest-item-price")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)

        private const val WILSON_BALL_PRICE = 45.30
        private const val FLASH_DRIVE_PRICE = 16.0
    }

    private fun item(productId: Long, price: Double, purchaseId: Long? = null) = PurchaseItem().apply {
        product = Product().apply { id = productId }
        amount = 1
        this.price = price
        purchaseId?.let { purchase = Purchase().apply { id = it } }
    }

    private suspend fun storedPrice(itemId: Long): Double? = env.purchaseItemRepo.fetchById(itemId)!!.price

    @Test
    fun insertedItem_takesTheProductsPrice_notTheOneSent() = runBlocking {
        val item = item(DBReset.WILSON_BALL_ID, price = 0.01, purchaseId = DBReset.ADMIN_FIRST_PURCHASE_ID)
        assertTrue(env.purchaseItemRepo.insert(item))
        assertEquals(WILSON_BALL_PRICE, storedPrice(item.id!!))
    }

    @Test
    fun purchaseInsertedWithItems_takesTheProductsPrices() = runBlocking {
        val purchase = Purchase().apply {
            buyDate = Clock.System.now()
            user = br.com.wdc.shopping.domain.user.User().apply { id = DBReset.FULANO_ID }
            items = mutableListOf(item(DBReset.WILSON_BALL_ID, 0.01), item(DBReset.FLASH_DRIVE_2GB_ID, 0.02))
        }
        assertTrue(env.purchaseRepo.insert(purchase))
        val prices = env.purchaseItemRepo.fetch(PurchaseItemCriteria().withPurchaseId(purchase.id)).map { it.productId to it.price }.toSet()
        assertEquals(setOf(DBReset.WILSON_BALL_ID to WILSON_BALL_PRICE, DBReset.FLASH_DRIVE_2GB_ID to FLASH_DRIVE_PRICE), prices)
    }

    @Test
    fun updatingThePriceOrTheProduct_resetsItToTheProductsPrice() = runBlocking {
        val item = item(DBReset.WILSON_BALL_ID, 0.01, DBReset.ADMIN_FIRST_PURCHASE_ID)
        assertTrue(env.purchaseItemRepo.insert(item))

        assertTrue(env.purchaseItemRepo.update(PurchaseItem().apply { id = item.id; price = 0.01 }, null, PurchaseItem().apply { price = ProjectionValues.f64 }))
        assertEquals(WILSON_BALL_PRICE, storedPrice(item.id!!), "the price sent is ignored")

        val moved = PurchaseItem().apply { id = item.id; product = Product().apply { id = DBReset.FLASH_DRIVE_2GB_ID } }
        assertTrue(env.purchaseItemRepo.update(moved, null, PurchaseItem().apply { product = Product().apply { id = ProjectionValues.i64 } }))
        assertEquals(FLASH_DRIVE_PRICE, storedPrice(item.id!!), "another product brings its price")

        // the amount alone does not touch the price
        assertTrue(env.purchaseItemRepo.update(PurchaseItem().apply { id = item.id; amount = 3 }, null, PurchaseItem().apply { amount = ProjectionValues.i32 }))
        assertEquals(FLASH_DRIVE_PRICE, storedPrice(item.id!!))
    }

    @Test
    fun itemOfAnUnknownProduct_isRefused() {
        val e = assertThrows(BusinessException::class.java) {
            runBlocking { env.purchaseItemRepo.insert(item(9_999_999L, 1.0, DBReset.ADMIN_FIRST_PURCHASE_ID)) }
        }
        assertTrue(e.message!!.startsWith("HTTP 400"), e.message)
    }
}
