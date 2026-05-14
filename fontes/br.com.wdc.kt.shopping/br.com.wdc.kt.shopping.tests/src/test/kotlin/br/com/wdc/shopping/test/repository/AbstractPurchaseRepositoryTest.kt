package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.criteria.PurchaseCriteria
import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.utils.ProjectionValues
import br.com.wdc.shopping.scripts.sgbd.DBReset
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.time.Clock

abstract class AbstractPurchaseRepositoryTest {

    protected abstract fun repo(): PurchaseRepository

    protected abstract fun purchaseItemRepo(): PurchaseItemRepository

    private fun purchaseProjectionWithUser(): Purchase {
        val pv = ProjectionValues
        val prj = Purchase()
        prj.id = pv.i64
        prj.buyDate = pv.offsetDateTime
        prj.user = User()
        prj.user!!.id = pv.i64
        return prj
    }

    // :: fetch

    @Test
    fun fetchAll_returnsSeededPurchases() {
        val purchases = repo().fetch(PurchaseCriteria())
        assertEquals(2, purchases.size)
    }

    @Test
    fun fetchById_returnsCorrectPurchase() {
        val purchase = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ID, purchaseProjectionWithUser())
        assertNotNull(purchase)
        assertNotNull(purchase!!.buyDate)
        assertNotNull(purchase.user)
        assertEquals(DBReset.ADMIN_ID, purchase.user!!.id)
    }

    @Test
    fun fetchById_nonExistent_returnsNull() {
        val purchase = repo().fetchById(Long.MAX_VALUE, null)
        assertNull(purchase)
    }

    @Test
    fun fetchWithProjection_onlyRequestedFields() {
        val pv = ProjectionValues
        val projection = Purchase()
        projection.id = pv.i64
        projection.buyDate = pv.offsetDateTime

        val purchase = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ID, projection)
        assertNotNull(purchase)
        assertEquals(DBReset.ADMIN_FIRST_PURCHASE_ID, purchase!!.id)
        assertNotNull(purchase.buyDate)
    }

    @Test
    fun fetchByUserId() {
        val criteria = PurchaseCriteria()
            .withUserId(DBReset.ADMIN_ID)
            .withProjection(purchaseProjectionWithUser())
        val purchases = repo().fetch(criteria)
        assertEquals(2, purchases.size)
        for (p in purchases) {
            assertEquals(DBReset.ADMIN_ID, p.user!!.id)
        }
    }

    @Test
    fun fetchByUserId_noResults() {
        val purchases = repo().fetch(PurchaseCriteria().withUserId(DBReset.FULANO_ID))
        assertTrue(purchases.isEmpty())
    }

    @Test
    fun fetchByPurchaseId() {
        val purchases = repo().fetch(PurchaseCriteria().withPurchaseId(DBReset.ADMIN_SECOND_PURCHASE_ID))
        assertEquals(1, purchases.size)
        assertEquals(DBReset.ADMIN_SECOND_PURCHASE_ID, purchases[0].id)
    }

    @Test
    fun fetchWithOffsetAndLimit() {
        val purchases = repo().fetch(
            PurchaseCriteria()
                .withOrderBy(PurchaseCriteria.OrderBy.ASCENDING)
                .withOffset(0)
                .withLimit(1)
        )
        assertEquals(1, purchases.size)
    }

    @Test
    fun fetchWithOrderAscending() {
        val purchases = repo().fetch(
            PurchaseCriteria()
                .withOrderBy(PurchaseCriteria.OrderBy.ASCENDING)
        )
        assertEquals(2, purchases.size)
        assertTrue(purchases[0].id!! <= purchases[1].id!!)
    }

    @Test
    fun fetchWithOrderDescending() {
        val purchases = repo().fetch(
            PurchaseCriteria()
                .withOrderBy(PurchaseCriteria.OrderBy.DESCENDING)
        )
        assertEquals(2, purchases.size)
        assertTrue(purchases[0].id!! >= purchases[1].id!!)
    }

    // :: count

    @Test
    fun countAll_returnsTwo() {
        val count = repo().count(PurchaseCriteria())
        assertEquals(2, count)
    }

    @Test
    fun countByUserId() {
        val count = repo().count(PurchaseCriteria().withUserId(DBReset.ADMIN_ID))
        assertEquals(2, count)
    }

    @Test
    fun countNonExistent_returnsZero() {
        val count = repo().count(PurchaseCriteria().withPurchaseId(Long.MAX_VALUE))
        assertEquals(0, count)
    }

    // :: insert

    @Test
    fun insert_newPurchase() {
        val purchase = Purchase()
        purchase.buyDate = Clock.System.now()
        purchase.user = User()
        purchase.user!!.id = DBReset.FULANO_ID

        val inserted = repo().insert(purchase)
        assertTrue(inserted)
        assertNotNull(purchase.id)

        val fetched = repo().fetchById(purchase.id!!, purchaseProjectionWithUser())
        assertNotNull(fetched)
        assertEquals(DBReset.FULANO_ID, fetched!!.user!!.id)
    }

    // :: update

    @Test
    fun update_existingPurchase() {
        val prj = purchaseProjectionWithUser()
        val original = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ID, prj)
        assertNotNull(original)

        val updated = Purchase()
        updated.id = original!!.id
        updated.buyDate = Clock.System.now()
        updated.user = User()
        updated.user!!.id = DBReset.BEOTRANO_ID

        val result = repo().update(updated, original)
        assertTrue(result)

        val fetched = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ID, prj)
        assertEquals(DBReset.BEOTRANO_ID, fetched!!.user!!.id)
    }

    // :: insertOrUpdate

    @Test
    fun insertOrUpdate_insertsWhenNew() {
        val purchase = Purchase()
        purchase.buyDate = Clock.System.now()
        purchase.user = User()
        purchase.user!!.id = DBReset.BEOTRANO_ID

        val result = repo().insertOrUpdate(purchase)
        assertTrue(result)
        assertNotNull(purchase.id)

        val fetched = repo().fetchById(purchase.id!!, purchaseProjectionWithUser())
        assertNotNull(fetched)
        assertEquals(DBReset.BEOTRANO_ID, fetched!!.user!!.id)
    }

    @Test
    fun insertOrUpdate_updatesWhenExisting() {
        val purchase = Purchase()
        purchase.id = DBReset.ADMIN_FIRST_PURCHASE_ID
        purchase.buyDate = Clock.System.now()
        purchase.user = User()
        purchase.user!!.id = DBReset.FULANO_ID

        val result = repo().insertOrUpdate(purchase)
        assertTrue(result)

        val fetched = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ID, purchaseProjectionWithUser())
        assertEquals(DBReset.FULANO_ID, fetched!!.user!!.id)
    }

    // :: delete

    @Test
    fun deleteByPurchaseId() {
        // First delete purchase items to avoid FK constraint
        purchaseItemRepo().delete(
            PurchaseItemCriteria().withPurchaseId(DBReset.ADMIN_FIRST_PURCHASE_ID)
        )

        val deleted = repo().delete(PurchaseCriteria().withPurchaseId(DBReset.ADMIN_FIRST_PURCHASE_ID))
        assertEquals(1, deleted)
        assertEquals(1, repo().count(PurchaseCriteria()))
    }

    @Test
    fun deleteByUserId() {
        purchaseItemRepo().delete(
            PurchaseItemCriteria().withUserId(DBReset.ADMIN_ID)
        )

        val deleted = repo().delete(PurchaseCriteria().withUserId(DBReset.ADMIN_ID))
        assertEquals(2, deleted)
        assertEquals(0, repo().count(PurchaseCriteria()))
    }

    @Test
    fun deleteNonExistent_returnsZero() {
        val deleted = repo().delete(PurchaseCriteria().withPurchaseId(Long.MAX_VALUE))
        assertEquals(0, deleted)
    }
}
