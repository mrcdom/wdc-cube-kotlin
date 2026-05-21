package br.com.wdc.shopping.domain.repositories

import br.com.wdc.shopping.domain.criteria.ProductCriteria
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.framework.commons.util.AtomicRef

interface ProductRepository {

    suspend fun insert(product: Product): Boolean

    suspend fun update(newProduct: Product, oldProduct: Product): Boolean

    suspend fun insertOrUpdate(product: Product): Boolean

    suspend fun delete(criteria: ProductCriteria): Int

    suspend fun count(criteria: ProductCriteria): Int

    suspend fun fetch(criteria: ProductCriteria): List<Product>

    suspend fun fetchPage(criteria: ProductCriteria): Page<Product>

    suspend fun fetchById(productId: Long, projection: Product?): Product?

    suspend fun fetchImage(productId: Long): ByteArray?

    suspend fun updateImage(productId: Long, image: ByteArray): Boolean

    companion object {
        val BEAN = AtomicRef<ProductRepository>()
    }
}
