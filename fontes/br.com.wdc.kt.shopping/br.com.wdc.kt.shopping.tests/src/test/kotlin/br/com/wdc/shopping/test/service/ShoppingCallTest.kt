package br.com.wdc.shopping.test.service

import br.com.wdc.shopping.domain.exception.InvalidCartItemException
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.presentation.exception.ProductNotFoundException
import br.com.wdc.shopping.presentation.presenter.open.login.calls.CredentialsCheck
import br.com.wdc.shopping.presentation.presenter.restricted.cart.calls.PurchaseCreate
import br.com.wdc.shopping.presentation.presenter.restricted.cart.structs.CartItem
import br.com.wdc.shopping.presentation.presenter.restricted.home.calls.PurchasesPageQuery
import br.com.wdc.shopping.presentation.presenter.restricted.products.calls.ProductListQuery
import br.com.wdc.shopping.presentation.presenter.restricted.products.calls.ProductQuery
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.calls.ReceiptQuery
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.TestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** The presentation's calls, in process: each reaches its repositories through their BEANs. */
class ShoppingCallTest {

    companion object {
        private val env = TestEnvironment("wedocode-shopping-calls")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private fun cartItem(productId: Long, quantity: Int, price: Double = 0.01) =
        CartItem().apply { id = productId; this.quantity = quantity; this.price = price; name = "p$productId" }

    @Test
    fun credentialsCheck_knowsTheUser_andRefusesAWrongPassword() = runBlocking {
        val subject = CredentialsCheck("admin", "admin").run()
        assertEquals(DBReset.ADMIN_ID, subject!!.id)
        assertEquals("John Smith", subject.nickName)
        assertNull(CredentialsCheck("admin", "wrong").run())
    }

    @Test
    fun productQueries_bringOneProduct_andTheListWithoutDescriptions() = runBlocking {
        val ball = ProductQuery(productId = DBReset.WILSON_BALL_ID).run()
        assertEquals("Wilson Ball", ball.name)
        assertFalse(ball.description.isNullOrBlank())
        assertThrows(ProductNotFoundException::class.java) { runBlocking { ProductQuery(productId = 987_654L).run() } }

        val list = ProductListQuery(limit = 10).run()
        assertEquals(listOf("Italian Design Coffee Maker", "Thread Seal Tape", "USB Flash Drive 2GB", "Wilson Ball"), list.map { it.name })
        assertTrue(list.all { it.description == null })
    }

    @Test
    fun purchaseCreate_writesThePurchaseWithTheProductsPrices_inOneTransaction() = runBlocking {
        val purchaseId = PurchaseCreate(DBReset.FULANO_ID, listOf(cartItem(DBReset.WILSON_BALL_ID, 2), cartItem(DBReset.FLASH_DRIVE_2GB_ID, 1))).run()

        val items = env.purchaseItemRepo.fetch(PurchaseItemCriteria().withPurchaseId(purchaseId)).associate { it.productId to it.price }
        assertEquals(mapOf(DBReset.WILSON_BALL_ID to 45.30, DBReset.FLASH_DRIVE_2GB_ID to 16.0), items, "the cart's price is not used")

        val page = PurchasesPageQuery(DBReset.FULANO_ID, page = 0, pageSize = 10).run()
        assertEquals(purchaseId, page.items.first().id, "the newest purchase comes first")

        val receipt = ReceiptQuery(purchaseId, DBReset.FULANO_ID).run()!!
        assertEquals(2 * 45.30 + 16.0, receipt.total!!, 1e-9)
        assertNull(ReceiptQuery(purchaseId, DBReset.BEOTRANO_ID).run(), "someone else's purchase does not exist")
    }

    @Test
    fun purchaseCreate_withAZeroQuantity_isRefused_andWritesNothing() = runBlocking {
        val before = PurchasesPageQuery(DBReset.FULANO_ID, 0, 10).run().totalItems
        assertThrows(InvalidCartItemException::class.java) {
            runBlocking { PurchaseCreate(DBReset.FULANO_ID, listOf(cartItem(DBReset.WILSON_BALL_ID, 0))).run() }
        }
        assertEquals(before, PurchasesPageQuery(DBReset.FULANO_ID, 0, 10).run().totalItems)
    }
}
