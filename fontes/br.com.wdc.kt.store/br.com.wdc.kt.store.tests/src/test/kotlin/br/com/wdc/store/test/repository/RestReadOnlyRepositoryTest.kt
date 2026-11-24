package br.com.wdc.store.test.repository

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.domain.repository.ReadOnlyRepository
import br.com.wdc.framework.domain.repository.Repository
import br.com.wdc.store.domain.product.Product
import br.com.wdc.store.domain.product.ProductCodec
import br.com.wdc.store.domain.product.ProductCriteria
import br.com.wdc.store.persistence.client.HttpReadOnlyRepository
import br.com.wdc.store.persistence.client.HttpTransport
import br.com.wdc.store.scripts.sgbd.DBReset
import br.com.wdc.store.test.util.RestTestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * A read-only repository over HTTP: the case of whoever only queries — a dashboard, a report. Here,
 * a catalog that reads the products through the API and has no way to write them.
 */
class RestReadOnlyRepositoryTest {

    /** Only the path and the lookup by key: the queries come from [HttpReadOnlyRepository]. */
    private class ProductCatalog(transport: HttpTransport) :
        HttpReadOnlyRepository<Product, ProductCriteria, Long>(transport, ProductCodec(), "/api/repo/product") {

        override fun ownFieldsProjection() = Product().apply { id = ProjectionValues.i64; name = ProjectionValues.str; price = ProjectionValues.f64 }

        override suspend fun fetchById(id: Long, projection: Product?): Product? =
            fetch(ProductCriteria().withProductId(id).withProjection(projection ?: ownFieldsProjection()), limit = 1).firstOrNull()
    }

    companion object {
        private val env = RestTestEnvironment("wedocode-store-rest-read-only")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private fun catalog() = ProductCatalog(env.transport)

    @Test
    fun readsThroughTheApi() = runBlocking {
        val catalog = catalog()
        assertEquals(4, catalog.count(ProductCriteria()))

        val cheapestFirst = ProductCriteria().withOrderBy(ProductCriteria.OrderBy.CHEAPEST_FIRST).withProjection(catalog.ownFieldsProjection())
        val products = catalog.fetch(cheapestFirst, limit = 2)
        assertEquals(listOf<Long?>(DBReset.THREAD_SEAL_TAPE_ID, DBReset.FLASH_DRIVE_2GB_ID), products.map { it.id })
        // the catalog projection does not ask for the description
        assertTrue(products.all { it.description == null })

        val page = catalog.fetchPage(cheapestFirst, 1, 3)
        assertEquals(1, page.items.size)
        assertEquals(4, page.totalItems)

        assertEquals("Italian", catalog.fetchById(DBReset.COFFEE_MAKER_ID)!!.name!!.substringBefore(' '))
        assertNull(catalog.fetchById(987_654L))
    }

    @Test
    fun theType_tellsReadersFromWriters() {
        val catalog: Any = catalog()
        assertTrue(catalog is ReadOnlyRepository<*, *, *>)
        assertFalse(catalog is Repository<*, *, *>)
        // the full repository of the same data serves where it is only read
        assertTrue(env.productRepo is ReadOnlyRepository<*, *, *>)
    }
}
