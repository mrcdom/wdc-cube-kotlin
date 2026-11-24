package br.com.wdc.store.test.transaction

import br.com.wdc.store.domain.exception.BusinessException
import br.com.wdc.store.domain.product.Product
import br.com.wdc.store.domain.purchase.Purchase
import br.com.wdc.store.domain.purchase.PurchaseCriteria
import br.com.wdc.store.domain.purchaseitem.PurchaseItem
import br.com.wdc.store.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.store.domain.user.User
import br.com.wdc.store.scripts.sgbd.DBReset
import br.com.wdc.store.test.util.RestTestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** Each REST write is a transaction isolated per request: either it writes everything the request brings, or nothing. */
class RestWriteTransactionTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-store-rest-write-tx")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private fun item(productId: Long) = PurchaseItem().apply { product = Product().apply { id = productId }; amount = 1; price = 10.5 }

    private fun purchaseOf(vararg productIds: Long) = Purchase().apply {
        buyDate = Clock.System.now()
        user = User().apply { id = DBReset.FULANO_ID }
        items = productIds.map(::item).toMutableList()
    }

    @Test
    fun insert_commitsThePurchaseAndItsItems() = runBlocking {
        val purchase = purchaseOf(DBReset.COFFEE_MAKER_ID, DBReset.FLASH_DRIVE_2GB_ID)
        assertTrue(env.purchaseRepo.insert(purchase))
        assertEquals(2, env.purchaseItemRepo.count(PurchaseItemCriteria().withPurchaseId(purchase.id)))
    }

    @Test
    fun insertThatFailsOnTheSecondItem_writesNothing() = runBlocking {
        assertThrows(BusinessException::class.java) {
            runBlocking { env.purchaseRepo.insert(purchaseOf(DBReset.COFFEE_MAKER_ID, 987_654L)) }
        }
        assertEquals(2, env.purchaseRepo.count(PurchaseCriteria()))
        assertEquals(3, env.purchaseItemRepo.count(PurchaseItemCriteria()))
    }

    @Test
    fun refusedWrite_keepsItsStatusCode() = runBlocking<Unit> {
        // the exception crosses the transaction with its original type: it is still 400, not 500
        val e = assertThrows(BusinessException::class.java) { runBlocking { env.purchaseRepo.delete(PurchaseCriteria()) } }
        assertTrue(e.message!!.startsWith("HTTP 400"), e.message)
    }
}
