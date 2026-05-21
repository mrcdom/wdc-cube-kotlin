package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.model.Product
import kotlinx.coroutines.runBlocking
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.TestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PurchaseItemRepositoryTest : AbstractPurchaseItemRepositoryTest() {

    companion object {
        private val env = TestEnvironment()

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    override fun repo(): PurchaseItemRepository = env.purchaseItemRepo

    // -- Testes exclusivos do modo LOCAL --

    @Test
    fun fetchById_returnsCorrectItem_withPurchase() = runBlocking {
        val item = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID, projectionWithRelations())
        assertNotNull(item)
        assertNotNull(item!!.purchase)
    }

    @Test
    fun insert_newPurchaseItem_withPurchaseAssertion() = runBlocking {
        val item = PurchaseItem()
        item.amount = 5
        item.price = 15.50
        item.purchase = Purchase()
        item.purchase!!.id = DBReset.ADMIN_FIRST_PURCHASE_ID
        item.product = Product()
        item.product!!.id = DBReset.PEN_DRIVE2GB_ID

        val inserted = repo().insert(item)
        assertTrue(inserted)

        val fetched = repo().fetchById(item.id!!, projectionWithRelations())
        assertNotNull(fetched)
        assertEquals(DBReset.ADMIN_FIRST_PURCHASE_ID, fetched!!.purchase!!.id)
        assertEquals(DBReset.PEN_DRIVE2GB_ID, fetched.product!!.id)
    }
}
