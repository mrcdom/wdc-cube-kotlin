package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import kotlinx.coroutines.runBlocking
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.utils.ProjectionValues
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.TestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PurchaseRepositoryTest : AbstractPurchaseRepositoryTest() {

    companion object {
        private val env = TestEnvironment()

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    override fun repo(): PurchaseRepository = env.purchaseRepo

    override fun purchaseItemRepo(): PurchaseItemRepository = env.purchaseItemRepo

    // -- Teste exclusivo do modo LOCAL (ProjectionList com sub-criteria) --

    @Test
    fun fetchWithProjectionList_filterItemsByCriteria() = runBlocking {
        val pv = ProjectionValues

        val itemPrj = PurchaseItem()
        itemPrj.id = pv.i64
        itemPrj.amount = pv.i32
        itemPrj.product = Product()
        itemPrj.product!!.id = pv.i64

        val itemCriteria = PurchaseItemCriteria()
            .withProductId(DBReset.BOLA_WILSON_ID)

        val projection = Purchase()
        projection.id = pv.i64
        projection.items = pv.singletonList(itemPrj, itemCriteria)

        val purchase = repo().fetchById(DBReset.ADMIN_SECOND_PURCHASE_ID, projection)
        assertNotNull(purchase)
        assertNotNull(purchase!!.items)
        assertEquals(1, purchase.items!!.size)
        assertEquals(DBReset.BOLA_WILSON_ID, purchase.items!![0].product!!.id)
    }
}
