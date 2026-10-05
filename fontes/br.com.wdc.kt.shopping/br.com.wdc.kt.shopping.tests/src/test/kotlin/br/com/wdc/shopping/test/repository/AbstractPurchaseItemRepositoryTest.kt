package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import kotlinx.coroutines.runBlocking
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.domain.exception.BusinessException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

abstract class AbstractPurchaseItemRepositoryTest {

    protected abstract fun repo(): PurchaseItemRepository

    protected fun projectionWithRelations(): PurchaseItem {
        val pv = ProjectionValues
        val prj = PurchaseItem()
        prj.id = pv.i64
        prj.amount = pv.i32
        prj.price = pv.f64
        prj.purchase = Purchase()
        prj.purchase!!.id = pv.i64
        prj.product = Product()
        prj.product!!.id = pv.i64
        return prj
    }

    // :: fetch

    @Test
    fun fetchAll_returnsAllSeededItems() = runBlocking {
        val items = repo().fetch(PurchaseItemCriteria())
        assertEquals(3, items.size)
    }

    @Test
    fun fetchById_returnsCorrectItem() = runBlocking {
        val item = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID, projectionWithRelations())
        assertNotNull(item)
        assertNotNull(item!!.amount)
        assertNotNull(item.price)
        assertNotNull(item.product)
    }

    @Test
    fun fetchById_nonExistent_returnsNull() = runBlocking {
        val item = repo().fetchById(Long.MAX_VALUE, null)
        assertNull(item)
    }

    @Test
    fun fetchWithProjection_onlyRequestedFields() = runBlocking {
        val pv = ProjectionValues
        val projection = PurchaseItem()
        projection.id = pv.i64
        projection.amount = pv.i32
        projection.price = pv.f64

        val item = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID, projection)
        assertNotNull(item)
        assertEquals(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID, item!!.id)
        assertNotNull(item.amount)
        assertNotNull(item.price)
    }

    @Test
    fun fetchByPurchaseId_firstPurchase() = runBlocking {
        val items = repo().fetch(
            PurchaseItemCriteria().withPurchaseId(DBReset.ADMIN_FIRST_PURCHASE_ID)
        )
        assertEquals(1, items.size)
        assertEquals(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID, items[0].id)
    }

    @Test
    fun fetchByPurchaseId_secondPurchase() = runBlocking {
        val items = repo().fetch(
            PurchaseItemCriteria().withPurchaseId(DBReset.ADMIN_SECOND_PURCHASE_ID)
        )
        assertEquals(2, items.size)
    }

    @Test
    fun fetchByUserId() = runBlocking {
        val items = repo().fetch(
            PurchaseItemCriteria().withUserId(DBReset.ADMIN_ID)
        )
        assertEquals(3, items.size)
    }

    @Test
    fun fetchByUserId_noResults() = runBlocking {
        val items = repo().fetch(
            PurchaseItemCriteria().withUserId(DBReset.FULANO_ID)
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun fetchByProductId() = runBlocking {
        val criteria = PurchaseItemCriteria()
            .withProductId(DBReset.CAFETEIRA_ID)
            .withProjection(projectionWithRelations())
        val items = repo().fetch(criteria)
        assertFalse(items.isEmpty())
        for (item in items) {
            assertEquals(DBReset.CAFETEIRA_ID, item.product!!.id)
        }
    }

    @Test
    fun fetchWithOffsetAndLimit() = runBlocking {
        val items = repo().fetch(
            PurchaseItemCriteria()
                .withOrderBy(PurchaseItemCriteria.OrderBy.OLDEST_FIRST),
            offset = 1, limit = 2,
        )
        assertEquals(listOf<Long?>(DBReset.ADMIN_SECOND_PURCHASE_ITEM0_ID, DBReset.ADMIN_SECOND_PURCHASE_ITEM1_ID), items.map { it.id })
    }

    @Test
    fun fetchWithOrderAscending() = runBlocking {
        val items = repo().fetch(
            PurchaseItemCriteria()
                .withOrderBy(PurchaseItemCriteria.OrderBy.OLDEST_FIRST)
        )
        assertEquals(3, items.size)
        for (i in 1 until items.size) {
            assertTrue(items[i - 1].id!! <= items[i].id!!)
        }
    }

    @Test
    fun fetchWithOrderDescending() = runBlocking {
        val items = repo().fetch(
            PurchaseItemCriteria()
                .withOrderBy(PurchaseItemCriteria.OrderBy.NEWEST_FIRST)
        )
        assertEquals(3, items.size)
        for (i in 1 until items.size) {
            assertTrue(items[i - 1].id!! >= items[i].id!!)
        }
    }

    // :: count

    @Test
    fun countAll_returnsThree() = runBlocking {
        val count = repo().count(PurchaseItemCriteria())
        assertEquals(3, count)
    }

    @Test
    fun countByPurchaseId() = runBlocking {
        val count = repo().count(
            PurchaseItemCriteria().withPurchaseId(DBReset.ADMIN_SECOND_PURCHASE_ID)
        )
        assertEquals(2, count)
    }

    @Test
    fun countByUserId() = runBlocking {
        val count = repo().count(
            PurchaseItemCriteria().withUserId(DBReset.ADMIN_ID)
        )
        assertEquals(3, count)
    }

    @Test
    fun countNonExistent_returnsZero() = runBlocking {
        val count = repo().count(
            PurchaseItemCriteria().withPurchaseItemId(Long.MAX_VALUE)
        )
        assertEquals(0, count)
    }

    // :: insert

    @Test
    fun insert_newPurchaseItem() = runBlocking {
        val item = PurchaseItem()
        item.amount = 5
        item.price = 15.50
        item.purchase = Purchase()
        item.purchase!!.id = DBReset.ADMIN_FIRST_PURCHASE_ID
        item.product = Product()
        item.product!!.id = DBReset.PEN_DRIVE2GB_ID

        val inserted = repo().insert(item)
        assertTrue(inserted)
        assertNotNull(item.id)

        val fetched = repo().fetchById(item.id!!, projectionWithRelations())
        assertNotNull(fetched)
        assertEquals(5, fetched!!.amount)
        assertEquals(15.50, fetched.price!!, 0.001)
        assertEquals(DBReset.PEN_DRIVE2GB_ID, fetched.product!!.id)
    }

    // :: update

    @Test
    fun update_existingPurchaseItem() = runBlocking {
        val original = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID, null)
        assertNotNull(original)

        val updated = PurchaseItem()
        updated.id = original!!.id
        updated.amount = 99
        updated.price = 999.99
        updated.purchase = original.purchase
        updated.product = original.product

        val result = repo().update(updated, original)
        assertTrue(result)

        val fetched = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID, null)
        assertEquals(99, fetched!!.amount)
        assertEquals(999.99, fetched.price!!, 0.001)
    }

    // :: insertOrUpdate

    @Test
    fun insertOrUpdate_insertsWhenNew() = runBlocking {
        val item = PurchaseItem()
        item.amount = 3
        item.price = 25.0
        item.purchase = Purchase()
        item.purchase!!.id = DBReset.ADMIN_SECOND_PURCHASE_ID
        item.product = Product()
        item.product!!.id = DBReset.BOLA_WILSON_ID

        val result = repo().insertOrUpdate(item, null)
        assertTrue(result)
        assertNotNull(item.id)

        assertEquals(4, repo().count(PurchaseItemCriteria()))
    }

    @Test
    fun insertOrUpdate_updatesWhenExisting() = runBlocking {
        val item = PurchaseItem()
        item.id = DBReset.ADMIN_SECOND_PURCHASE_ITEM0_ID
        item.amount = 77
        item.price = 77.77
        item.purchase = Purchase()
        item.purchase!!.id = DBReset.ADMIN_SECOND_PURCHASE_ID
        item.product = Product()
        item.product!!.id = DBReset.CAFETEIRA_ID

        val old = repo().fetchById(DBReset.ADMIN_SECOND_PURCHASE_ITEM0_ID)
        val result = repo().insertOrUpdate(item, old)
        assertTrue(result)

        val fetched = repo().fetchById(DBReset.ADMIN_SECOND_PURCHASE_ITEM0_ID, null)
        assertEquals(77, fetched!!.amount)
        assertEquals(77.77, fetched.price!!, 0.001)
        assertEquals(DBReset.CAFETEIRA_ID, fetched.productId)
        assertEquals(DBReset.ADMIN_SECOND_PURCHASE_ID, fetched.purchaseId)
    }

    // :: delete

    @Test
    fun deleteByPurchaseItemId() = runBlocking {
        val deleted = repo().delete(
            PurchaseItemCriteria().withPurchaseItemId(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID)
        )
        assertEquals(1, deleted)
        assertEquals(2, repo().count(PurchaseItemCriteria()))
    }

    @Test
    fun deleteByPurchaseId() = runBlocking {
        val deleted = repo().delete(
            PurchaseItemCriteria().withPurchaseId(DBReset.ADMIN_SECOND_PURCHASE_ID)
        )
        assertEquals(2, deleted)
        assertEquals(1, repo().count(PurchaseItemCriteria()))
    }

    @Test
    fun deleteByUserId_crossEntityExists() = runBlocking {
        val deleted = repo().delete(
            PurchaseItemCriteria().withUserId(DBReset.ADMIN_ID)
        )
        assertEquals(3, deleted)
        assertEquals(0, repo().count(PurchaseItemCriteria()))
    }

    @Test
    fun deleteByUserId_noResults() = runBlocking {
        val deleted = repo().delete(
            PurchaseItemCriteria().withUserId(DBReset.FULANO_ID)
        )
        assertEquals(0, deleted)
        assertEquals(3, repo().count(PurchaseItemCriteria()))
    }

    @Test
    fun deleteNonExistent_returnsZero() = runBlocking {
        val deleted = repo().delete(
            PurchaseItemCriteria().withPurchaseItemId(Long.MAX_VALUE)
        )
        assertEquals(0, deleted)
    }

    // :: Relações

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

    @Test
    fun fetchWithPurchaseAndItsUser() = runBlocking {
        val pv = ProjectionValues
        val projection = PurchaseItem().apply {
            id = pv.i64
            purchase = Purchase().apply { id = pv.i64; buyDate = pv.instant }
            product = Product().apply { id = pv.i64; name = pv.str; price = pv.f64 }
        }
        val item = repo().fetchById(DBReset.ADMIN_SECOND_PURCHASE_ITEM1_ID, projection)!!
        assertEquals(DBReset.ADMIN_SECOND_PURCHASE_ID, item.purchaseId)
        assertEquals("Fita veda rosca", item.product!!.name)
        assertNull(item.amount)
    }

    // :: Critério expressivo e ordenação

    @Test
    fun criteria_comparisons() = runBlocking {
        val i0 = DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID
        val i1 = DBReset.ADMIN_SECOND_PURCHASE_ITEM0_ID
        val i2 = DBReset.ADMIN_SECOND_PURCHASE_ITEM1_ID
        fun byId() = PurchaseItemCriteria().withOrderBy(PurchaseItemCriteria.OrderBy.OLDEST_FIRST)

        assertEquals(listOf<Long?>(i0, i1), ids(byId().also { it.price.gt(10.5) }))
        assertEquals(listOf<Long?>(i2), ids(byId().withPrice(2.67)))
        assertEquals(listOf<Long?>(i1, i2), ids(byId().also { it.price.between(2.5, 50.5) }))
        assertEquals(listOf<Long?>(i0, i1, i2), ids(byId().withAmount(1)))
        assertEquals(emptyList<Long?>(), ids(byId().also { it.amount.gt(1) }))
        assertEquals(listOf<Long?>(i0, i2), ids(byId().also { it.productId.isIn(listOf(DBReset.CAFETEIRA_ID, DBReset.FITA_VEDA_ROSCA_ID)) }))
        assertEquals(listOf<Long?>(i0, i2), ids(byId().also { it.purchaseItemId.or().eq(i0); it.purchaseItemId.eq(i2) }))
        // os campos acumulam em AND — inclusive o que se resolve pela compra
        assertEquals(listOf<Long?>(i1), ids(byId().withUserId(DBReset.ADMIN_ID).withProductId(DBReset.BOLA_WILSON_ID)))
        assertEquals(emptyList<Long?>(), ids(byId().withUserId(DBReset.FULANO_ID).withProductId(DBReset.BOLA_WILSON_ID)))
    }

    @Test
    fun orderBy_priceAndQuantity() = runBlocking {
        val i0 = DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID
        val i1 = DBReset.ADMIN_SECOND_PURCHASE_ITEM0_ID
        val i2 = DBReset.ADMIN_SECOND_PURCHASE_ITEM1_ID
        assertEquals(listOf<Long?>(i0, i1, i2), ids(PurchaseItemCriteria().withOrderBy(PurchaseItemCriteria.OrderBy.MOST_EXPENSIVE_FIRST)))

        assertTrue(repo().update(PurchaseItem().apply { id = i2; amount = 7 }, null, PurchaseItem().apply { amount = ProjectionValues.i32 }))
        // empate na quantidade desempata pela chave
        assertEquals(listOf<Long?>(i2, i0, i1), ids(PurchaseItemCriteria().withOrderBy(PurchaseItemCriteria.OrderBy.LARGEST_QUANTITY_FIRST)))
    }

    @Test
    fun fetchPage_reportsTheTotalAndThePages() = runBlocking {
        val page = repo().fetchPage(PurchaseItemCriteria().withOrderBy(PurchaseItemCriteria.OrderBy.OLDEST_FIRST), 1, 2)
        assertEquals(listOf<Long?>(DBReset.ADMIN_SECOND_PURCHASE_ITEM1_ID), page.items.map { it.id })
        assertEquals(3, page.totalItems)
        assertEquals(2, page.totalPages)
    }

    // :: Escrita

    @Test
    fun update_movesTheItemToAnotherPurchase_withoutTouchingTheProduct() = runBlocking {
        val id = DBReset.ADMIN_SECOND_PURCHASE_ITEM0_ID
        val old = repo().fetchById(id)!!
        val moved = PurchaseItem().apply {
            this.id = id
            amount = old.amount
            price = old.price
            product = old.product
            purchase = Purchase().apply { this.id = DBReset.ADMIN_FIRST_PURCHASE_ID }
        }
        assertTrue(repo().update(moved, old))

        val fetched = repo().fetchById(id)!!
        assertEquals(DBReset.ADMIN_FIRST_PURCHASE_ID, fetched.purchaseId)
        assertEquals(DBReset.BOLA_WILSON_ID, fetched.productId)
    }

    @Test
    fun update_onlyTheProjectedFields() = runBlocking {
        val id = DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID
        val changed = PurchaseItem().apply { this.id = id; amount = 4; price = 1.5 }
        assertTrue(repo().update(changed, null, PurchaseItem().apply { amount = ProjectionValues.i32 }))

        val fetched = repo().fetchById(id)!!
        assertEquals(4, fetched.amount)
        assertEquals(200.0, fetched.price)
        assertEquals(DBReset.CAFETEIRA_ID, fetched.productId)
    }

    @Test
    fun update_withoutChanges_writesNothing() = runBlocking {
        val old = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID)!!
        val same = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID)!!
        assertFalse(repo().update(same, old))
    }

    @Test
    fun update_withoutId_isRefused() = runBlocking<Unit> {
        assertThrows(BusinessException::class.java) {
            runBlocking { repo().update(PurchaseItem().apply { amount = 2 }) }
        }
    }

    @Test
    fun amount_mustBePositive() = runBlocking {
        val item = PurchaseItem().apply {
            amount = 0
            price = 1.5
            purchase = Purchase().apply { id = DBReset.ADMIN_FIRST_PURCHASE_ID }
            product = Product().apply { id = DBReset.PEN_DRIVE2GB_ID }
        }
        assertThrows(BusinessException::class.java) { runBlocking { repo().insert(item) } }
        assertEquals(3, repo().count(PurchaseItemCriteria()))
    }

    @Test
    fun delete_withEmptyCriteria_isRefused_andDeletesNothing() = runBlocking {
        assertThrows(BusinessException::class.java) {
            runBlocking { repo().delete(PurchaseItemCriteria()) }
        }
        assertEquals(3, repo().count(PurchaseItemCriteria()))
    }

    private suspend fun ids(criteria: PurchaseItemCriteria): List<Long?> = repo().fetch(criteria).map { it.id }
}
