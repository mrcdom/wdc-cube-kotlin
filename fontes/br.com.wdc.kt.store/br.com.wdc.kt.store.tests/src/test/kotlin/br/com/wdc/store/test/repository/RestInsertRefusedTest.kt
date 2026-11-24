package br.com.wdc.store.test.repository

import br.com.wdc.framework.domain.repository.Repository
import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.rpc.RepositoryRpc
import br.com.wdc.framework.rest.testing.RestTestServer
import br.com.wdc.store.domain.product.Product
import br.com.wdc.store.domain.product.ProductCodec
import br.com.wdc.store.domain.product.ProductCriteria
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** An `insert` the repository refuses (`false`): the API answers only `{"success":false}`, without an id. */
class RestInsertRefusedTest {

    /** Takes a key and then inserts nothing — as an insert with "on conflict do nothing" that met a conflict. */
    private class RefusingRepository : Repository<Product, ProductCriteria, Long> {
        override suspend fun insert(bean: Product, projection: Product?): Boolean {
            bean.id = 99L
            return false
        }

        override suspend fun update(newBean: Product, oldBean: Product?, projection: Product?) = error("not used")
        override suspend fun delete(criteria: ProductCriteria) = error("not used")
        override suspend fun count(criteria: ProductCriteria) = error("not used")
        override suspend fun fetch(criteria: ProductCriteria, offset: Int, limit: Int) = error("not used")
        override suspend fun fetchById(id: Long, projection: Product?) = error("not used")
        override fun ownFieldsProjection() = Product()
    }

    @Test
    fun insertThatInsertsNothing_answersWithoutId() {
        val repository = RefusingRepository()
        val api = RestApi {
            RepositoryRpc<Product, ProductCriteria, Long>(
                basePath = "/api/repo/product",
                codec = ProductCodec(),
                repository = { repository },
                newCriteria = ::ProductCriteria,
                setProjection = { c, p -> c.withProjection(p) },
                restrictToId = { c, id -> c.withProductId(id) },
                parseId = String::toLongOrNull,
                idOf = { it.id },
            ).register(this)
        }
        RestTestServer(api).use { server ->
            val response = server.client().post("/api/repo/product/insert", """{"entity":{"name":"x","price":1.5}}""")
            assertEquals(200, response.status)
            // the id of a row that doesn't exist would mislead (as in the C# version)
            assertEquals("""{"success":false}""", response.body)
        }
    }
}
