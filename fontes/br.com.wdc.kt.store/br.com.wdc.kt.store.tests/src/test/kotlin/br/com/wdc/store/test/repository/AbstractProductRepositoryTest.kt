package br.com.wdc.store.test.repository

import br.com.wdc.framework.domain.exception.NotFoundException
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.store.domain.exception.BusinessException
import br.com.wdc.store.domain.product.Product
import br.com.wdc.store.domain.product.ProductCriteria
import br.com.wdc.store.domain.product.ProductCriteria.OrderBy
import br.com.wdc.store.domain.product.ProductRepository
import br.com.wdc.store.scripts.sgbd.DBReset
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * The product repository contract. The subclasses say which way: straight to the implementation (LOCAL) or
 * through REST — the same cases must hold in both.
 *
 * Demo seed: Italian Design Coffee Maker (199.99), Wilson Ball (45.30), Thread Seal Tape (2.67) and
 * USB Flash Drive 2GB (16.00), with increasing ids in that order.
 */
abstract class AbstractProductRepositoryTest {

    protected abstract fun repo(): ProductRepository

    private suspend fun ids(criteria: ProductCriteria, offset: Int = 0, limit: Int = 0): List<Long?> =
        repo().fetch(criteria.withProjection(Product().apply { id = ProjectionValues.i64 }), offset, limit).map { it.id }

    private fun byIdAsc() = ProductCriteria().withOrderBy(OrderBy.OLDEST_FIRST)

    private val coffeeMaker get() = DBReset.COFFEE_MAKER_ID
    private val ball get() = DBReset.WILSON_BALL_ID
    private val tape get() = DBReset.THREAD_SEAL_TAPE_ID
    private val flashDrive get() = DBReset.FLASH_DRIVE_2GB_ID

    // :: fetch

    @Test
    fun fetchAll_returnsFourProducts() = runBlocking {
        val products = repo().fetch(ProductCriteria())
        assertEquals(4, products.size)
    }

    @Test
    fun fetch_defaultProjection_bringsEverythingButTheImage() = runBlocking {
        val product = repo().fetch(ProductCriteria().withProductId(coffeeMaker)).single()
        assertEquals(coffeeMaker, product.id)
        assertEquals("Italian Design Coffee Maker", product.name)
        assertEquals(199.99, product.price!!, 0.001)
        assertTrue(product.description!!.contains("Capacity"))
        assertNull(product.image)
    }

    @Test
    fun fetchById_returnsCorrectProduct() = runBlocking {
        val product = repo().fetchById(coffeeMaker, null)
        assertNotNull(product)
        assertNotNull(product!!.name)
        assertNotNull(product.price)
    }

    @Test
    fun fetchById_nonExistent_returnsNull() = runBlocking {
        assertNull(repo().fetchById(Long.MAX_VALUE, null))
    }

    @Test
    fun fetchWithProjection_onlyRequestedFields() = runBlocking {
        val pv = ProjectionValues
        val projection = Product().apply { id = pv.i64; name = pv.str }

        val product = repo().fetchById(flashDrive, projection)

        assertEquals(flashDrive, product!!.id)
        assertEquals("USB Flash Drive 2GB", product.name)
        assertNull(product.price)
        assertNull(product.description)
    }

    @Test
    fun fetchWithProjection_withoutId_stillBringsTheId() = runBlocking {
        val product = repo().fetch(ProductCriteria().withProductId(ball).withProjection(Product().apply { name = ProjectionValues.str })).single()
        assertEquals(ball, product.id)
        assertEquals("Wilson Ball", product.name)
    }

    @Test
    fun fetchByCriteria_productId() = runBlocking {
        val products = repo().fetch(ProductCriteria().withProductId(ball))
        assertEquals(1, products.size)
        assertEquals(ball, products[0].id)
    }

    @Test
    fun fetchWithOffsetAndLimit() = runBlocking {
        assertEquals(listOf<Long?>(coffeeMaker, ball), ids(byIdAsc(), limit = 2))
        assertEquals(listOf<Long?>(ball, tape), ids(byIdAsc(), offset = 1, limit = 2))
        assertEquals(listOf<Long?>(tape, flashDrive), ids(byIdAsc(), offset = 2))
        assertEquals(emptyList<Long?>(), ids(byIdAsc(), offset = 10, limit = 2))
    }

    @Test
    fun fetchPage_bringsTheSliceAndTheTotals() = runBlocking {
        val page = repo().fetchPage(byIdAsc(), page = 1, pageSize = 3)
        assertEquals(listOf<Long?>(flashDrive), page.items.map { it.id })
        assertEquals(1, page.page)
        assertEquals(2, page.totalPages)
        assertEquals(4, page.totalItems)

        val first = repo().fetchPage(byIdAsc(), page = 0, pageSize = 3)
        assertEquals(listOf<Long?>(coffeeMaker, ball, tape), first.items.map { it.id })
    }

    @Test
    fun fetchPage_countsOnlyWhatTheCriteriaSelects() = runBlocking {
        val page = repo().fetchPage(ProductCriteria().also { it.price.lt(50.0) }.withOrderBy(OrderBy.OLDEST_FIRST), page = 0, pageSize = 2)
        assertEquals(listOf<Long?>(ball, tape), page.items.map { it.id })
        assertEquals(3, page.totalItems)
        assertEquals(2, page.totalPages)
    }

    // :: orderings

    @Test
    fun orderBy_everyConstant() = runBlocking {
        assertEquals(listOf<Long?>(coffeeMaker, ball, tape, flashDrive), ids(ProductCriteria().withOrderBy(OrderBy.OLDEST_FIRST)))
        assertEquals(listOf<Long?>(flashDrive, tape, ball, coffeeMaker), ids(ProductCriteria().withOrderBy(OrderBy.NEWEST_FIRST)))
        assertEquals(listOf<Long?>(coffeeMaker, tape, flashDrive, ball), ids(ProductCriteria().withOrderBy(OrderBy.NAME_A_TO_Z)))
        assertEquals(listOf<Long?>(tape, flashDrive, ball, coffeeMaker), ids(ProductCriteria().withOrderBy(OrderBy.CHEAPEST_FIRST)))
        assertEquals(listOf<Long?>(coffeeMaker, ball, flashDrive, tape), ids(ProductCriteria().withOrderBy(OrderBy.MOST_EXPENSIVE_FIRST)))
    }

    // :: operators

    @Test
    fun criteria_isIn_andNe() = runBlocking {
        assertEquals(listOf<Long?>(coffeeMaker, tape), ids(byIdAsc().also { it.productId.isIn(coffeeMaker, tape, Long.MAX_VALUE) }))
        assertEquals(listOf<Long?>(ball, flashDrive), ids(byIdAsc().also { it.productId.ne(coffeeMaker); it.productId.ne(tape) }))
    }

    @Test
    fun criteria_isIn_emptySet_matchesNothing() = runBlocking {
        val none = { ProductCriteria().also { it.productId.isIn(emptyList()) } }
        assertEquals(emptyList<Long?>(), ids(none()))
        assertEquals(0, repo().count(none()))
        assertEquals(0, repo().delete(none()))
        assertEquals(0, repo().delete(ProductCriteria().also { it.productId.isIn() }))
        assertEquals(4, repo().count(ProductCriteria()))
        // null is "not provided": it does not filter
        assertEquals(4, repo().count(ProductCriteria().also { it.productId.isIn(null as Collection<Long>?) }))
    }

    @Test
    fun criteria_comparisons() = runBlocking {
        assertEquals(listOf<Long?>(coffeeMaker), ids(byIdAsc().also { it.price.gt(45.30) }))
        assertEquals(listOf<Long?>(coffeeMaker, ball), ids(byIdAsc().also { it.price.ge(45.30) }))
        assertEquals(listOf<Long?>(tape), ids(byIdAsc().also { it.price.lt(16.0) }))
        assertEquals(listOf<Long?>(tape, flashDrive), ids(byIdAsc().also { it.price.le(16.0) }))
    }

    @Test
    fun criteria_between_isInclusive_andAcceptsOpenBounds() = runBlocking {
        assertEquals(listOf<Long?>(ball, flashDrive), ids(byIdAsc().also { it.price.between(16.0, 45.30) }))
        assertEquals(listOf<Long?>(coffeeMaker, ball), ids(byIdAsc().also { it.price.between(45.30, null) }))
        assertEquals(listOf<Long?>(tape, flashDrive), ids(byIdAsc().also { it.price.between(null, 16.0) }))
        assertEquals(4, ids(byIdAsc().also { it.price.between(null, null) }).size)
    }

    @Test
    fun criteria_successiveRequestsAreAnd_orIsPerField_andFieldsAreAlwaysAnd() = runBlocking {
        assertEquals(listOf<Long?>(ball, flashDrive), ids(byIdAsc().also { it.price.ge(16.0); it.price.le(45.30) }))
        assertEquals(listOf<Long?>(coffeeMaker, tape), ids(byIdAsc().also { it.price.or().lt(16.0); it.price.gt(45.30) }))
        assertEquals(listOf<Long?>(coffeeMaker), ids(byIdAsc().also { it.price.or().lt(16.0); it.price.gt(45.30); it.name.startingWith("ital") }))
    }

    @Test
    fun criteria_emptyAndCleared_filterNothing() = runBlocking {
        assertEquals(4, ids(ProductCriteria()).size)
        val criteria = byIdAsc().also { it.productId.eq(coffeeMaker) }
        assertEquals(1, ids(criteria).size)
        criteria.productId.clear()
        assertEquals(4, ids(criteria).size)
        // a null value adds no condition
        assertEquals(4, ids(ProductCriteria().withProductId(null).withName(null).also { it.price.ge(null) }).size)
    }

    // :: text

    @Test
    fun criteria_text() = runBlocking {
        assertEquals(listOf<Long?>(ball), ids(byIdAsc().also { it.name.startingWith("wilson") }))
        // two conditions on the same field hold together: names with "o" and with "n"
        assertEquals(listOf<Long?>(coffeeMaker, ball), ids(byIdAsc().also { it.name.containing("o"); it.name.containing("n") }))
        assertEquals(listOf<Long?>(coffeeMaker, tape), ids(byIdAsc().also { it.description.containing("Drip-stop"); it.description.or().containing("Tigre") }))
        assertEquals(listOf<Long?>(flashDrive), ids(byIdAsc().also { it.name.ilike("usb flash%") }))
        assertEquals(listOf<Long?>(flashDrive), ids(byIdAsc().also { it.name.eq("USB Flash Drive 2GB") }))
        // wildcards are part of the value: without them, like is equality
        assertEquals(emptyList<Long?>(), ids(byIdAsc().also { it.description.like("Tigre brand") }))
        assertEquals(listOf<Long?>(tape), ids(byIdAsc().also { it.description.like("%Tigre brand%") }))
        // like is case-sensitive (in the description; the name is a column that ignores case in H2)
        assertEquals(emptyList<Long?>(), ids(byIdAsc().also { it.description.like("%tigre brand%") }))
        assertEquals(listOf<Long?>(tape), ids(byIdAsc().also { it.description.ilike("%tigre brand%") }))
    }

    @Test
    fun criteria_emptyText_filtersNothing() = runBlocking {
        assertEquals(4, ids(ProductCriteria().also { it.name.containing(""); it.name.startingWith(null); it.description.like("") }).size)
    }

    // :: count

    @Test
    fun countAll_returnsFour() = runBlocking {
        assertEquals(4, repo().count(ProductCriteria()))
    }

    @Test
    fun countByProductId_returnsOne() = runBlocking {
        assertEquals(1, repo().count(ProductCriteria().withProductId(coffeeMaker)))
    }

    @Test
    fun countNonExistent_returnsZero() = runBlocking {
        assertEquals(0, repo().count(ProductCriteria().withProductId(Long.MAX_VALUE)))
    }

    @Test
    fun count_honoursTheOperators() = runBlocking {
        assertEquals(3, repo().count(ProductCriteria().also { it.price.lt(100.0) }))
    }

    // :: image

    @Test
    fun fetchImage_returnsNonNullForSeededProduct() = runBlocking {
        val image = repo().fetchImage(coffeeMaker)
        assertNotNull(image)
        assertTrue(image!!.isNotEmpty())
    }

    @Test
    fun fetchImage_nonExistent_isNotFound_notNoImage() = runBlocking {
        // by HTTP it was a 500, and the client took a 404 for "no image" (204)
        assertThrows(NotFoundException::class.java) {
            runBlocking { repo().fetchImage(Long.MAX_VALUE) }
        }
    }

    @Test
    fun updateImage_replacesTheImage_withItsRealSize() = runBlocking {
        val image = ByteArray(1500) { (it % 127).toByte() }
        assertTrue(repo().updateImage(ball, image))
        assertArrayEquals(image, repo().fetchImage(ball))
        // the rest of the product does not change
        assertEquals("Wilson Ball", repo().fetchById(ball)!!.name)
    }

    @Test
    fun insertedProduct_hasNoImage() = runBlocking {
        val product = Product().apply { name = "No photo"; price = 1.0; description = "d" }
        repo().insert(product)
        assertNull(repo().fetchImage(product.id!!))
    }

    // :: insert

    @Test
    fun insert_withProjection_writesOnlyTheMarkedFields() = runBlocking {
        val product = Product().apply { name = "With photo"; price = 3.5; description = "d"; image = byteArrayOf(1, 2, 3) }
        val projection = Product().apply { name = ""; price = 0.0; description = "" }
        assertTrue(repo().insert(product, projection))
        assertNotNull(product.id, "the key comes back")
        assertNull(repo().fetchImage(product.id!!), "outside the projection: not written")
        assertEquals("With photo", repo().fetchById(product.id!!)!!.name)
    }

    @Test
    fun insert_newProduct() = runBlocking {
        val product = Product().apply {
            name = "USB Keyboard"
            price = 89.90
            description = "Mechanical keyboard"
        }

        assertTrue(repo().insert(product))
        assertNotNull(product.id)

        val fetched = repo().fetchById(product.id!!, null)
        assertEquals("USB Keyboard", fetched!!.name)
        assertEquals(89.90, fetched.price!!, 0.001)
        assertTrue(fetched.description!!.contains("keyboard"))
    }

    // :: update

    @Test
    fun update_existingProduct() = runBlocking {
        val original = repo().fetchById(flashDrive, null)!!

        val updated = Product().apply {
            id = original.id
            name = "USB Flash Drive 4GB"
            price = 35.0
            description = original.description
        }
        assertTrue(repo().update(updated, original))

        val fetched = repo().fetchById(flashDrive, null)
        assertEquals("USB Flash Drive 4GB", fetched!!.name)
        assertEquals(35.0, fetched.price!!, 0.001)
        assertEquals(original.description, fetched.description)
    }

    @Test
    fun update_withProjection_changesOnlyTheMarkedFields() = runBlocking {
        val original = repo().fetchById(flashDrive)!!

        val changes = Product().apply { id = flashDrive; name = "Only the name"; price = 999.0 }
        assertTrue(repo().update(changes, null, Product().apply { name = ProjectionValues.str }))

        val fetched = repo().fetchById(flashDrive)!!
        assertEquals("Only the name", fetched.name)
        assertEquals(original.price!!, fetched.price!!, 0.001)
        assertEquals(original.description, fetched.description)
    }

    @Test
    fun update_withoutProjectionNorOldBean_writesOnlyTheFieldsWithValue() = runBlocking {
        val original = repo().fetchById(flashDrive)!!
        // a bean read with a narrow projection (id, name), changed and sent back: what it did not read must not be cleared
        assertTrue(repo().update(Product().apply { id = flashDrive; name = "Only the name, no projection" }))

        val fetched = repo().fetchById(flashDrive)!!
        assertEquals("Only the name, no projection", fetched.name)
        assertEquals(original.price!!, fetched.price!!, 0.001)
        assertEquals(original.description, fetched.description)
    }

    @Test
    fun update_withoutChanges_returnsFalse() = runBlocking {
        val original = repo().fetchById(flashDrive)!!
        val same = Product().apply { id = original.id; name = original.name; price = original.price; description = original.description }
        assertFalse(repo().update(same, original))
    }

    @Test
    fun update_nonExistent_returnsFalse() = runBlocking {
        val ghost = Product().apply { id = Long.MAX_VALUE; name = "x"; price = 1.0; description = "d" }
        assertFalse(repo().update(ghost))
    }

    @Test
    fun update_withoutId_isRefused() = runBlocking<Unit> {
        assertThrows(BusinessException::class.java) {
            runBlocking { repo().update(Product().apply { name = "no id" }) }
        }
    }

    // :: insertOrUpdate

    @Test
    fun insertOrUpdate_insertsWhenThereIsNoOldBean() = runBlocking {
        val product = Product().apply {
            name = "Mouse Wireless"
            price = 49.90
            description = "Wireless mouse"
        }

        assertTrue(repo().insertOrUpdate(product, null))
        assertNotNull(product.id)
        assertEquals(5, repo().count(ProductCriteria()))
    }

    @Test
    fun insertOrUpdate_updatesWhenThereIsAnOldBean() = runBlocking {
        val original = repo().fetchById(tape, null)!!

        val product = Product().apply {
            id = tape
            name = "Thread Seal Tape Premium"
            price = 12.0
            description = original.description
        }
        assertTrue(repo().insertOrUpdate(product, original))

        assertEquals("Thread Seal Tape Premium", repo().fetchById(tape, null)!!.name)
        assertEquals(4, repo().count(ProductCriteria()))
    }

    // :: delete

    @Test
    fun delete_byCriteria() = runBlocking {
        val keyboard = Product().apply { name = "Keyboard"; price = 80.0; description = "d" }
        val mouse = Product().apply { name = "Mouse"; price = 40.0; description = "d" }
        repo().insert(keyboard)
        repo().insert(mouse)

        assertEquals(1, repo().delete(ProductCriteria().withProductId(keyboard.id)))
        assertEquals(1, repo().delete(ProductCriteria().also { it.name.eq("Mouse") }))
        assertEquals(4, repo().count(ProductCriteria()))
    }

    @Test
    fun deleteNonExistent_returnsZero() = runBlocking {
        assertEquals(0, repo().delete(ProductCriteria().withProductId(Long.MAX_VALUE)))
    }

    @Test
    fun delete_withEmptyCriteria_isRefused_andDeletesNothing() = runBlocking {
        assertThrows(BusinessException::class.java) {
            runBlocking { repo().delete(ProductCriteria()) }
        }
        // criteria with only an ordering still has no filter
        assertThrows(BusinessException::class.java) {
            runBlocking { repo().delete(ProductCriteria().withOrderBy(OrderBy.OLDEST_FIRST)) }
        }
        assertEquals(4, repo().count(ProductCriteria()))
    }
}
