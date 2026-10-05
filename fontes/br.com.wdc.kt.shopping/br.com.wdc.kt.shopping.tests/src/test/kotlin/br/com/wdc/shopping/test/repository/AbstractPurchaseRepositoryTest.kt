package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import kotlinx.coroutines.runBlocking
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.scripts.sgbd.DBReset
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import br.com.wdc.shopping.domain.exception.BusinessException
import kotlin.time.Clock
import kotlin.time.Instant

abstract class AbstractPurchaseRepositoryTest {

    protected abstract fun repo(): PurchaseRepository

    protected abstract fun purchaseItemRepo(): PurchaseItemRepository

    private fun purchaseProjectionWithUser(): Purchase {
        val pv = ProjectionValues
        val prj = Purchase()
        prj.id = pv.i64
        prj.buyDate = pv.instant
        prj.user = User()
        prj.user!!.id = pv.i64
        return prj
    }

    // :: fetch

    @Test
    fun fetchAll_returnsSeededPurchases() = runBlocking {
        val purchases = repo().fetch(PurchaseCriteria())
        assertEquals(2, purchases.size)
    }

    @Test
    fun fetchById_returnsCorrectPurchase() = runBlocking {
        val purchase = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ID, purchaseProjectionWithUser())
        assertNotNull(purchase)
        assertNotNull(purchase!!.buyDate)
        assertNotNull(purchase.user)
        assertEquals(DBReset.ADMIN_ID, purchase.user!!.id)
    }

    @Test
    fun fetchById_nonExistent_returnsNull() = runBlocking {
        val purchase = repo().fetchById(Long.MAX_VALUE, null)
        assertNull(purchase)
    }

    @Test
    fun fetchWithProjection_onlyRequestedFields() = runBlocking {
        val pv = ProjectionValues
        val projection = Purchase()
        projection.id = pv.i64
        projection.buyDate = pv.instant

        val purchase = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ID, projection)
        assertNotNull(purchase)
        assertEquals(DBReset.ADMIN_FIRST_PURCHASE_ID, purchase!!.id)
        assertNotNull(purchase.buyDate)
    }

    @Test
    fun fetchByUserId() = runBlocking {
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
    fun fetchByUserId_noResults() = runBlocking {
        val purchases = repo().fetch(PurchaseCriteria().withUserId(DBReset.FULANO_ID))
        assertTrue(purchases.isEmpty())
    }

    @Test
    fun fetchByPurchaseId() = runBlocking {
        val purchases = repo().fetch(PurchaseCriteria().withPurchaseId(DBReset.ADMIN_SECOND_PURCHASE_ID))
        assertEquals(1, purchases.size)
        assertEquals(DBReset.ADMIN_SECOND_PURCHASE_ID, purchases[0].id)
    }

    @Test
    fun fetchWithOffsetAndLimit() = runBlocking {
        val purchases = repo().fetch(
            PurchaseCriteria()
                .withOrderBy(PurchaseCriteria.OrderBy.OLDEST_FIRST),
            offset = 1, limit = 1,
        )
        assertEquals(listOf<Long?>(DBReset.ADMIN_SECOND_PURCHASE_ID), purchases.map { it.id })
    }

    @Test
    fun fetchWithOrderAscending() = runBlocking {
        val purchases = repo().fetch(
            PurchaseCriteria()
                .withOrderBy(PurchaseCriteria.OrderBy.OLDEST_FIRST)
        )
        assertEquals(2, purchases.size)
        assertTrue(purchases[0].id!! <= purchases[1].id!!)
    }

    @Test
    fun fetchWithOrderDescending() = runBlocking {
        val purchases = repo().fetch(
            PurchaseCriteria()
                .withOrderBy(PurchaseCriteria.OrderBy.NEWEST_FIRST)
        )
        assertEquals(2, purchases.size)
        assertTrue(purchases[0].id!! >= purchases[1].id!!)
    }

    // :: count

    @Test
    fun countAll_returnsTwo() = runBlocking {
        val count = repo().count(PurchaseCriteria())
        assertEquals(2, count)
    }

    @Test
    fun countByUserId() = runBlocking {
        val count = repo().count(PurchaseCriteria().withUserId(DBReset.ADMIN_ID))
        assertEquals(2, count)
    }

    @Test
    fun countNonExistent_returnsZero() = runBlocking {
        val count = repo().count(PurchaseCriteria().withPurchaseId(Long.MAX_VALUE))
        assertEquals(0, count)
    }

    // :: insert

    @Test
    fun insert_newPurchase() = runBlocking {
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
    fun update_existingPurchase() = runBlocking {
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
    fun insertOrUpdate_insertsWhenNew() = runBlocking {
        val purchase = Purchase()
        purchase.buyDate = Clock.System.now()
        purchase.user = User()
        purchase.user!!.id = DBReset.BEOTRANO_ID

        val result = repo().insertOrUpdate(purchase, null)
        assertTrue(result)
        assertNotNull(purchase.id)

        val fetched = repo().fetchById(purchase.id!!, purchaseProjectionWithUser())
        assertNotNull(fetched)
        assertEquals(DBReset.BEOTRANO_ID, fetched!!.user!!.id)
    }

    @Test
    fun insertOrUpdate_updatesWhenExisting() = runBlocking {
        val purchase = Purchase()
        purchase.id = DBReset.ADMIN_FIRST_PURCHASE_ID
        purchase.buyDate = Clock.System.now()
        purchase.user = User()
        purchase.user!!.id = DBReset.FULANO_ID

        val old = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ID, purchaseProjectionWithUser())
        val result = repo().insertOrUpdate(purchase, old)
        assertTrue(result)

        val fetched = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ID, purchaseProjectionWithUser())
        assertEquals(DBReset.FULANO_ID, fetched!!.user!!.id)
    }

    // :: delete

    @Test
    fun deleteByPurchaseId_removesItsItemsFirst() = runBlocking {
        val deleted = repo().delete(PurchaseCriteria().withPurchaseId(DBReset.ADMIN_FIRST_PURCHASE_ID))
        assertEquals(1, deleted)
        assertEquals(1, repo().count(PurchaseCriteria()))
        assertEquals(2, purchaseItemRepo().count(PurchaseItemCriteria()))
    }

    @Test
    fun deleteByUserId() = runBlocking {
        val deleted = repo().delete(PurchaseCriteria().withUserId(DBReset.ADMIN_ID))
        assertEquals(2, deleted)
        assertEquals(0, repo().count(PurchaseCriteria()))
        assertEquals(0, purchaseItemRepo().count(PurchaseItemCriteria()))
    }

    @Test
    fun deleteByProductId_resolvesThePurchasesBeforeRemovingTheItems() = runBlocking {
        val deleted = repo().delete(PurchaseCriteria().withProductId(DBReset.FITA_VEDA_ROSCA_ID))
        assertEquals(1, deleted)
        assertEquals(listOf<Long?>(DBReset.ADMIN_FIRST_PURCHASE_ID), repo().fetch(PurchaseCriteria()).map { it.id })
        assertEquals(1, purchaseItemRepo().count(PurchaseItemCriteria()))
    }

    @Test
    fun deleteNonExistent_returnsZero() = runBlocking {
        val deleted = repo().delete(PurchaseCriteria().withPurchaseId(Long.MAX_VALUE))
        assertEquals(0, deleted)
    }

    @Test
    fun delete_withEmptyCriteria_isRefused_andDeletesNothing() = runBlocking {
        assertThrows(BusinessException::class.java) {
            runBlocking { repo().delete(PurchaseCriteria()) }
        }
        assertEquals(2, repo().count(PurchaseCriteria()))
        assertEquals(3, purchaseItemRepo().count(PurchaseItemCriteria()))
    }

    @Test
    fun update_withoutId_isRefused() = runBlocking<Unit> {
        assertThrows(BusinessException::class.java) {
            runBlocking { repo().update(Purchase().apply { buyDate = Clock.System.now() }) }
        }
    }

    // :: A data da compra

    @Test
    fun buyDate_isTheSameInstantOnTheWayInAndOut() = runBlocking {
        // a carga grava 2010-01-01 14:30 e 2011-04-03 09:15, em UTC
        val seeded = repo().fetchById(DBReset.ADMIN_FIRST_PURCHASE_ID)!!
        assertEquals(Instant.parse("2010-01-01T14:30:00Z"), seeded.buyDate)

        val moment = Instant.parse("2024-07-15T21:45:10Z")
        val purchase = Purchase().apply { buyDate = moment; user = User().apply { id = DBReset.FULANO_ID } }
        assertTrue(repo().insert(purchase))
        assertEquals(moment, repo().fetchById(purchase.id!!)!!.buyDate)
    }

    @Test
    fun criteria_buyDate() = runBlocking {
        val first = DBReset.ADMIN_FIRST_PURCHASE_ID
        val second = DBReset.ADMIN_SECOND_PURCHASE_ID
        fun byId() = PurchaseCriteria().withOrderBy(PurchaseCriteria.OrderBy.OLDEST_FIRST)

        assertEquals(listOf<Long?>(first), ids(byId().withBuyDate(Instant.parse("2010-01-01T14:30:00Z"))))
        assertEquals(listOf<Long?>(second), ids(byId().also { it.buyDate.gt(Instant.parse("2010-01-01T14:30:00Z")) }))
        assertEquals(listOf<Long?>(first, second), ids(byId().also { it.buyDate.ge(Instant.parse("2010-01-01T14:30:00Z")) }))
        assertEquals(
            listOf<Long?>(second),
            ids(byId().also { it.buyDate.between(Instant.parse("2011-01-01T00:00:00Z"), Instant.parse("2011-12-31T23:59:59Z")) }),
        )
    }

    // :: Critério expressivo e ordenação

    @Test
    fun criteria_productId_reachesThePurchaseThroughItsItems() = runBlocking {
        val first = DBReset.ADMIN_FIRST_PURCHASE_ID
        val second = DBReset.ADMIN_SECOND_PURCHASE_ID
        fun byId() = PurchaseCriteria().withOrderBy(PurchaseCriteria.OrderBy.OLDEST_FIRST)

        assertEquals(listOf<Long?>(first), ids(byId().withProductId(DBReset.CAFETEIRA_ID)))
        assertEquals(listOf<Long?>(second), ids(byId().withProductId(DBReset.BOLA_WILSON_ID)))
        assertEquals(emptyList<Long?>(), ids(byId().withProductId(DBReset.PEN_DRIVE2GB_ID)))
        assertEquals(
            listOf<Long?>(first, second),
            ids(byId().also { it.productId.isIn(listOf(DBReset.CAFETEIRA_ID, DBReset.FITA_VEDA_ROSCA_ID)) }),
        )
        assertEquals(1, repo().count(PurchaseCriteria().withProductId(DBReset.CAFETEIRA_ID)))
        // os campos acumulam em AND
        assertEquals(emptyList<Long?>(), ids(byId().withProductId(DBReset.CAFETEIRA_ID).withPurchaseId(second)))
    }

    @Test
    fun criteria_or_withinAField() = runBlocking {
        val both = PurchaseCriteria().withOrderBy(PurchaseCriteria.OrderBy.OLDEST_FIRST).also {
            it.purchaseId.or().eq(DBReset.ADMIN_FIRST_PURCHASE_ID)
            it.purchaseId.eq(DBReset.ADMIN_SECOND_PURCHASE_ID)
        }
        assertEquals(listOf<Long?>(DBReset.ADMIN_FIRST_PURCHASE_ID, DBReset.ADMIN_SECOND_PURCHASE_ID), ids(both))
    }

    @Test
    fun orderBy_purchaseDate() = runBlocking {
        val first = DBReset.ADMIN_FIRST_PURCHASE_ID
        val second = DBReset.ADMIN_SECOND_PURCHASE_ID
        assertEquals(listOf<Long?>(second, first), ids(PurchaseCriteria().withOrderBy(PurchaseCriteria.OrderBy.MOST_RECENT_PURCHASE_FIRST)))
        assertEquals(listOf<Long?>(first, second), ids(PurchaseCriteria().withOrderBy(PurchaseCriteria.OrderBy.EARLIEST_PURCHASE_FIRST)))
    }

    @Test
    fun fetchPage_reportsTheTotalAndThePages() = runBlocking {
        val criteria = PurchaseCriteria().withOrderBy(PurchaseCriteria.OrderBy.OLDEST_FIRST)
        val page = repo().fetchPage(criteria, 1, 1)
        assertEquals(listOf<Long?>(DBReset.ADMIN_SECOND_PURCHASE_ID), page.items.map { it.id })
        assertEquals(2, page.totalItems)
        assertEquals(2, page.totalPages)
        assertEquals(1, page.page)
    }

    // :: Itens da compra

    @Test
    fun insert_cascadesTheItems() = runBlocking {
        val purchase = Purchase().apply {
            buyDate = Clock.System.now()
            user = User().apply { id = DBReset.FULANO_ID }
            items = mutableListOf(
                PurchaseItem().apply { product = Product().apply { id = DBReset.CAFETEIRA_ID }; amount = 2; price = 199.5 },
                PurchaseItem().apply { product = Product().apply { id = DBReset.PEN_DRIVE2GB_ID }; amount = 1; price = 16.25 },
            )
        }
        assertTrue(repo().insert(purchase))

        val stored = purchaseItemRepo().fetch(
            PurchaseItemCriteria().withPurchaseId(purchase.id).withOrderBy(PurchaseItemCriteria.OrderBy.MOST_EXPENSIVE_FIRST)
        )
        assertEquals(listOf<Long?>(DBReset.CAFETEIRA_ID, DBReset.PEN_DRIVE2GB_ID), stored.map { it.productId })
        assertEquals(listOf<Int?>(2, 1), stored.map { it.amount })
        assertEquals(listOf<Double?>(199.5, 16.25), stored.map { it.price })
        assertTrue(stored.all { it.purchaseId == purchase.id })
    }

    @Test
    fun fetchWithItems_andTheirProducts() = runBlocking {
        val pv = ProjectionValues
        val projection = Purchase().apply {
            id = pv.i64
            user = User().apply { id = pv.i64; name = pv.str }
            items = mutableListOf(
                PurchaseItem().apply { id = pv.i64; price = pv.f64; product = Product().apply { id = pv.i64; name = pv.str } }
            )
        }
        val purchase = repo().fetchById(DBReset.ADMIN_SECOND_PURCHASE_ID, projection)!!
        assertEquals("João da Silva", purchase.user!!.name)
        assertNull(purchase.buyDate)
        assertEquals(setOf<String?>("Bola Wilson", "Fita veda rosca"), purchase.items!!.map { it.product!!.name }.toSet())
        assertTrue(purchase.items!!.all { it.amount == null })
    }

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

    @Test
    fun fetchWithProjectionList_ordersAndSlicesTheItems() = runBlocking {
        val pv = ProjectionValues
        fun itemPrj() = PurchaseItem().apply { id = pv.i64; price = pv.f64 }
        fun itemsOf(items: MutableList<PurchaseItem>): List<Double?> {
            val projection = Purchase().apply { id = pv.i64; this.items = items }
            return runBlocking { repo().fetchById(DBReset.ADMIN_SECOND_PURCHASE_ID, projection) }!!.items!!.map { it.price }
        }

        val expensiveFirst = PurchaseItemCriteria().withOrderBy(PurchaseItemCriteria.OrderBy.MOST_EXPENSIVE_FIRST)
        assertEquals(listOf<Double?>(45.3, 2.67), itemsOf(pv.singletonList(itemPrj(), expensiveFirst)))

        val newestFirst = PurchaseItemCriteria().withOrderBy(PurchaseItemCriteria.OrderBy.NEWEST_FIRST)
        assertEquals(listOf<Double?>(2.67, 45.3), itemsOf(pv.singletonList(itemPrj(), newestFirst)))

        assertEquals(listOf<Double?>(45.3), itemsOf(pv.singletonList(itemPrj(), expensiveFirst).withLimit(1)))
        assertEquals(listOf<Double?>(2.67), itemsOf(pv.singletonList(itemPrj(), expensiveFirst).withLimit(1).withOffset(1)))
    }

    private suspend fun ids(criteria: PurchaseCriteria): List<Long?> = repo().fetch(criteria).map { it.id }
}
