package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.utils.ProjectionValues
import br.com.wdc.shopping.scripts.sgbd.DBReset
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
    fun fetchAll_returnsAllSeededItems() {
        val items = repo().fetch(PurchaseItemCriteria())
        assertEquals(3, items.size)
    }

    @Test
    fun fetchById_returnsCorrectItem() {
        val item = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID, projectionWithRelations())
        assertNotNull(item)
        assertNotNull(item!!.amount)
        assertNotNull(item.price)
        assertNotNull(item.product)
    }

    @Test
    fun fetchById_nonExistent_returnsNull() {
        val item = repo().fetchById(Long.MAX_VALUE, null)
        assertNull(item)
    }

    @Test
    fun fetchWithProjection_onlyRequestedFields() {
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
    fun fetchByPurchaseId_firstPurchase() {
        val items = repo().fetch(
            PurchaseItemCriteria().withPurchaseId(DBReset.ADMIN_FIRST_PURCHASE_ID)
        )
        assertEquals(1, items.size)
        assertEquals(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID, items[0].id)
    }

    @Test
    fun fetchByPurchaseId_secondPurchase() {
        val items = repo().fetch(
            PurchaseItemCriteria().withPurchaseId(DBReset.ADMIN_SECOND_PURCHASE_ID)
        )
        assertEquals(2, items.size)
    }

    @Test
    fun fetchByUserId() {
        val items = repo().fetch(
            PurchaseItemCriteria().withUserId(DBReset.ADMIN_ID)
        )
        assertEquals(3, items.size)
    }

    @Test
    fun fetchByUserId_noResults() {
        val items = repo().fetch(
            PurchaseItemCriteria().withUserId(DBReset.FULANO_ID)
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun fetchByProductId() {
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
    fun fetchWithOffsetAndLimit() {
        val items = repo().fetch(
            PurchaseItemCriteria()
                .withOrderBy(PurchaseItemCriteria.OrderBy.ACENDING)
                .withOffset(0)
                .withLimit(2)
        )
        assertEquals(2, items.size)
    }

    @Test
    fun fetchWithOrderAscending() {
        val items = repo().fetch(
            PurchaseItemCriteria()
                .withOrderBy(PurchaseItemCriteria.OrderBy.ACENDING)
        )
        assertEquals(3, items.size)
        for (i in 1 until items.size) {
            assertTrue(items[i - 1].id!! <= items[i].id!!)
        }
    }

    @Test
    fun fetchWithOrderDescending() {
        val items = repo().fetch(
            PurchaseItemCriteria()
                .withOrderBy(PurchaseItemCriteria.OrderBy.DESCENDING)
        )
        assertEquals(3, items.size)
        for (i in 1 until items.size) {
            assertTrue(items[i - 1].id!! >= items[i].id!!)
        }
    }

    // :: count

    @Test
    fun countAll_returnsThree() {
        val count = repo().count(PurchaseItemCriteria())
        assertEquals(3, count)
    }

    @Test
    fun countByPurchaseId() {
        val count = repo().count(
            PurchaseItemCriteria().withPurchaseId(DBReset.ADMIN_SECOND_PURCHASE_ID)
        )
        assertEquals(2, count)
    }

    @Test
    fun countByUserId() {
        val count = repo().count(
            PurchaseItemCriteria().withUserId(DBReset.ADMIN_ID)
        )
        assertEquals(3, count)
    }

    @Test
    fun countNonExistent_returnsZero() {
        val count = repo().count(
            PurchaseItemCriteria().withPurchaseItemId(Long.MAX_VALUE)
        )
        assertEquals(0, count)
    }

    // :: insert

    @Test
    fun insert_newPurchaseItem() {
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
    fun update_existingPurchaseItem() {
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
    fun insertOrUpdate_insertsWhenNew() {
        val item = PurchaseItem()
        item.amount = 3
        item.price = 25.0
        item.purchase = Purchase()
        item.purchase!!.id = DBReset.ADMIN_SECOND_PURCHASE_ID
        item.product = Product()
        item.product!!.id = DBReset.BOLA_WILSON_ID

        val result = repo().insertOrUpdate(item)
        assertTrue(result)
        assertNotNull(item.id)

        assertEquals(4, repo().count(PurchaseItemCriteria()))
    }

    @Test
    fun insertOrUpdate_updatesWhenExisting() {
        val item = PurchaseItem()
        item.id = DBReset.ADMIN_SECOND_PURCHASE_ITEM0_ID
        item.amount = 77
        item.price = 77.77
        item.purchase = Purchase()
        item.purchase!!.id = DBReset.ADMIN_SECOND_PURCHASE_ID
        item.product = Product()
        item.product!!.id = DBReset.CAFETEIRA_ID

        val result = repo().insertOrUpdate(item)
        assertTrue(result)

        val fetched = repo().fetchById(DBReset.ADMIN_SECOND_PURCHASE_ITEM0_ID, null)
        assertEquals(77, fetched!!.amount)
        assertEquals(77.77, fetched.price!!, 0.001)
    }

    // :: delete

    @Test
    fun deleteByPurchaseItemId() {
        val deleted = repo().delete(
            PurchaseItemCriteria().withPurchaseItemId(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID)
        )
        assertEquals(1, deleted)
        assertEquals(2, repo().count(PurchaseItemCriteria()))
    }

    @Test
    fun deleteByPurchaseId() {
        val deleted = repo().delete(
            PurchaseItemCriteria().withPurchaseId(DBReset.ADMIN_SECOND_PURCHASE_ID)
        )
        assertEquals(2, deleted)
        assertEquals(1, repo().count(PurchaseItemCriteria()))
    }

    @Test
    fun deleteByUserId_crossEntityExists() {
        val deleted = repo().delete(
            PurchaseItemCriteria().withUserId(DBReset.ADMIN_ID)
        )
        assertEquals(3, deleted)
        assertEquals(0, repo().count(PurchaseItemCriteria()))
    }

    @Test
    fun deleteByUserId_noResults() {
        val deleted = repo().delete(
            PurchaseItemCriteria().withUserId(DBReset.FULANO_ID)
        )
        assertEquals(0, deleted)
        assertEquals(3, repo().count(PurchaseItemCriteria()))
    }

    @Test
    fun deleteNonExistent_returnsZero() {
        val deleted = repo().delete(
            PurchaseItemCriteria().withPurchaseItemId(Long.MAX_VALUE)
        )
        assertEquals(0, deleted)
    }
}
