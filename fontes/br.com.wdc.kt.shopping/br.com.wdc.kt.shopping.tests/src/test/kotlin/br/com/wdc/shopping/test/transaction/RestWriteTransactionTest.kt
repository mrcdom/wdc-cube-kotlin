package br.com.wdc.shopping.test.transaction

import br.com.wdc.shopping.domain.exception.BusinessException
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** Cada escrita REST é uma transação isolada por requisição: ou grava tudo o que o pedido traz, ou nada. */
class RestWriteTransactionTest {

    companion object {
        private val env = RestTestEnvironment("wedocode-shopping-rest-write-tx")

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
        val purchase = purchaseOf(DBReset.CAFETEIRA_ID, DBReset.PEN_DRIVE2GB_ID)
        assertTrue(env.purchaseRepo.insert(purchase))
        assertEquals(2, env.purchaseItemRepo.count(PurchaseItemCriteria().withPurchaseId(purchase.id)))
    }

    @Test
    fun insertThatFailsOnTheSecondItem_writesNothing() = runBlocking {
        assertThrows(BusinessException::class.java) {
            runBlocking { env.purchaseRepo.insert(purchaseOf(DBReset.CAFETEIRA_ID, 987_654L)) }
        }
        assertEquals(2, env.purchaseRepo.count(PurchaseCriteria()))
        assertEquals(3, env.purchaseItemRepo.count(PurchaseItemCriteria()))
    }

    @Test
    fun refusedWrite_keepsItsStatusCode() = runBlocking<Unit> {
        // a exceção atravessa a transação com o tipo original: continua sendo 400, e não 500
        val e = assertThrows(BusinessException::class.java) { runBlocking { env.purchaseRepo.delete(PurchaseCriteria()) } }
        assertTrue(e.message!!.startsWith("HTTP 400"), e.message)
    }
}
