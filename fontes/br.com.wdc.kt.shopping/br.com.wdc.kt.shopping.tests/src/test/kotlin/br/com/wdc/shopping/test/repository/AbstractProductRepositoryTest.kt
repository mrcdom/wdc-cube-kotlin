package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.criteria.ProductCriteria
import br.com.wdc.shopping.domain.exception.BusinessException
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.utils.ProjectionValues
import br.com.wdc.shopping.scripts.sgbd.DBReset
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

abstract class AbstractProductRepositoryTest {

    protected abstract fun repo(): ProductRepository

    // :: fetch

    @Test
    fun fetchAll_returnsFourProducts() {
        val products = repo().fetch(ProductCriteria())
        assertEquals(4, products.size)
    }

    @Test
    fun fetchById_returnsCorrectProduct() {
        val product = repo().fetchById(DBReset.CAFETEIRA_ID, null)
        assertNotNull(product)
        assertNotNull(product!!.name)
        assertNotNull(product.price)
    }

    @Test
    fun fetchById_nonExistent_returnsNull() {
        val product = repo().fetchById(Long.MAX_VALUE, null)
        assertNull(product)
    }

    @Test
    fun fetchWithProjection_onlyRequestedFields() {
        val pv = ProjectionValues
        val projection = Product()
        projection.id = pv.i64
        projection.name = pv.str

        val product = repo().fetchById(DBReset.PEN_DRIVE2GB_ID, projection)
        assertNotNull(product)
        assertEquals(DBReset.PEN_DRIVE2GB_ID, product!!.id)
        assertNotNull(product.name)
    }

    @Test
    fun fetchByCriteria_productId() {
        val products = repo().fetch(ProductCriteria().withProductId(DBReset.BOLA_WILSON_ID))
        assertEquals(1, products.size)
        assertEquals(DBReset.BOLA_WILSON_ID, products[0].id)
    }

    @Test
    fun fetchWithOffsetAndLimit() {
        val products = repo().fetch(
            ProductCriteria()
                .withOrderBy(ProductCriteria.OrderBy.ACENDING)
                .withOffset(0)
                .withLimit(2)
        )
        assertEquals(2, products.size)
    }

    @Test
    fun fetchWithOrderAscending() {
        val products = repo().fetch(
            ProductCriteria()
                .withOrderBy(ProductCriteria.OrderBy.ACENDING)
        )
        assertEquals(4, products.size)
        for (i in 1 until products.size) {
            assertTrue(products[i - 1].id!! <= products[i].id!!)
        }
    }

    @Test
    fun fetchWithOrderDescending() {
        val products = repo().fetch(
            ProductCriteria()
                .withOrderBy(ProductCriteria.OrderBy.DESCENDING)
        )
        assertEquals(4, products.size)
        for (i in 1 until products.size) {
            assertTrue(products[i - 1].id!! >= products[i].id!!)
        }
    }

    // :: count

    @Test
    fun countAll_returnsFour() {
        val count = repo().count(ProductCriteria())
        assertEquals(4, count)
    }

    @Test
    fun countByProductId_returnsOne() {
        val count = repo().count(ProductCriteria().withProductId(DBReset.CAFETEIRA_ID))
        assertEquals(1, count)
    }

    @Test
    fun countNonExistent_returnsZero() {
        val count = repo().count(ProductCriteria().withProductId(Long.MAX_VALUE))
        assertEquals(0, count)
    }

    // :: fetchImage

    @Test
    fun fetchImage_returnsNonNullForSeededProduct() {
        val image = repo().fetchImage(DBReset.CAFETEIRA_ID)
        assertNotNull(image)
        assertTrue(image!!.isNotEmpty())
    }

    @Test
    fun fetchImage_nonExistent_throws() {
        assertThrows(BusinessException::class.java) {
            repo().fetchImage(Long.MAX_VALUE)
        }
    }

    // :: insert

    @Test
    fun insert_newProduct() {
        val product = Product()
        product.name = "Teclado USB"
        product.price = 89.90
        product.description = "Teclado mecanico"

        val inserted = repo().insert(product)
        assertTrue(inserted)
        assertNotNull(product.id)

        val fetched = repo().fetchById(product.id!!, null)
        assertNotNull(fetched)
        assertEquals("Teclado USB", fetched!!.name)
        assertEquals(89.90, fetched.price!!, 0.001)
        assertNotNull(fetched.description)
        assertTrue(fetched.description!!.contains("Teclado"))
    }

    // :: update

    @Test
    fun update_existingProduct() {
        val original = repo().fetchById(DBReset.PEN_DRIVE2GB_ID, null)
        assertNotNull(original)

        val updated = Product()
        updated.id = original!!.id
        updated.name = "Pen Drive 4GB"
        updated.price = 35.0
        updated.description = original.description

        val result = repo().update(updated, original)
        assertTrue(result)

        val fetched = repo().fetchById(DBReset.PEN_DRIVE2GB_ID, null)
        assertEquals("Pen Drive 4GB", fetched!!.name)
        assertEquals(35.0, fetched.price!!, 0.001)
    }

    // :: insertOrUpdate

    @Test
    fun insertOrUpdate_insertsWhenNew() {
        val product = Product()
        product.name = "Mouse Wireless"
        product.price = 49.90
        product.description = "Mouse sem fio"

        val result = repo().insertOrUpdate(product)
        assertTrue(result)
        assertNotNull(product.id)

        assertEquals(5, repo().count(ProductCriteria()))
    }

    @Test
    fun insertOrUpdate_updatesWhenExisting() {
        val original = repo().fetchById(DBReset.FITA_VEDA_ROSCA_ID, null)
        assertNotNull(original)

        val product = Product()
        product.id = DBReset.FITA_VEDA_ROSCA_ID
        product.name = "Fita Veda Rosca Premium"
        product.price = 12.0
        product.description = original!!.description

        val result = repo().insertOrUpdate(product)
        assertTrue(result)

        val fetched = repo().fetchById(DBReset.FITA_VEDA_ROSCA_ID, null)
        assertEquals("Fita Veda Rosca Premium", fetched!!.name)
    }

    // :: delete

    @Test
    fun deleteByProductId() {
        val deleted = repo().delete(ProductCriteria().withProductId(DBReset.PEN_DRIVE2GB_ID))
        assertEquals(1, deleted)
        assertEquals(3, repo().count(ProductCriteria()))
    }

    @Test
    fun deleteNonExistent_returnsZero() {
        val deleted = repo().delete(ProductCriteria().withProductId(Long.MAX_VALUE))
        assertEquals(0, deleted)
    }
}
