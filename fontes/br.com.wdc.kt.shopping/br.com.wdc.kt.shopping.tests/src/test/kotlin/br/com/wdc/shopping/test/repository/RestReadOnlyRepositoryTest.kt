package br.com.wdc.shopping.test.repository

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.domain.repository.ReadOnlyRepository
import br.com.wdc.framework.domain.repository.Repository
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.product.ProductCodec
import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.persistence.client.HttpReadOnlyRepository
import br.com.wdc.shopping.persistence.client.HttpTransport
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.RestTestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Um repositório de somente leitura sobre HTTP: o caso de quem só consulta — um painel, um relatório. Aqui,
 * um catálogo que lê os produtos pela API e não tem como gravá-los.
 */
class RestReadOnlyRepositoryTest {

    /** Só o caminho e a busca pela chave: as consultas vêm de [HttpReadOnlyRepository]. */
    private class ProductCatalog(transport: HttpTransport) :
        HttpReadOnlyRepository<Product, ProductCriteria, Long>(transport, ProductCodec(), "/api/repo/product") {

        override fun newProjection() = Product().apply { id = ProjectionValues.i64; name = ProjectionValues.str; price = ProjectionValues.f64 }

        override suspend fun fetchById(id: Long, projection: Product?): Product? =
            fetch(ProductCriteria().withProductId(id).withProjection(projection ?: newProjection()), limit = 1).firstOrNull()
    }

    companion object {
        private val env = RestTestEnvironment("wedocode-shopping-rest-read-only")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private fun catalog() = ProductCatalog(env.transport)

    @Test
    fun readsThroughTheApi() = runBlocking {
        val catalog = catalog()
        assertEquals(4, catalog.count(ProductCriteria()))

        val cheapestFirst = ProductCriteria().withOrderBy(ProductCriteria.OrderBy.CHEAPEST_FIRST).withProjection(catalog.newProjection())
        val products = catalog.fetch(cheapestFirst, limit = 2)
        assertEquals(listOf<Long?>(DBReset.FITA_VEDA_ROSCA_ID, DBReset.PEN_DRIVE2GB_ID), products.map { it.id })
        // a projeção do catálogo não pede a descrição
        assertTrue(products.all { it.description == null })

        val page = catalog.fetchPage(cheapestFirst, 1, 3)
        assertEquals(1, page.items.size)
        assertEquals(4, page.totalItems)

        assertEquals("Cafeteira", catalog.fetchById(DBReset.CAFETEIRA_ID)!!.name!!.substringBefore(' '))
        assertNull(catalog.fetchById(987_654L))
    }

    @Test
    fun theType_tellsReadersFromWriters() {
        val catalog: Any = catalog()
        assertTrue(catalog is ReadOnlyRepository<*, *, *>)
        assertFalse(catalog is Repository<*, *, *>)
        // o repositório completo do mesmo dado serve onde só se lê
        assertTrue(env.productRepo is ReadOnlyRepository<*, *, *>)
    }
}
