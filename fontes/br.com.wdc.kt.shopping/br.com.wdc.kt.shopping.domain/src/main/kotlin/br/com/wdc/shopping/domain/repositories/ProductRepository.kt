package br.com.wdc.shopping.domain.repositories

import br.com.wdc.shopping.domain.criteria.ProductCriteria
import br.com.wdc.shopping.domain.model.Product
import java.util.concurrent.atomic.AtomicReference

interface ProductRepository {

    fun insert(product: Product): Boolean

    fun update(newProduct: Product, oldProduct: Product): Boolean

    fun insertOrUpdate(product: Product): Boolean

    fun delete(criteria: ProductCriteria): Int

    fun count(criteria: ProductCriteria): Int

    fun fetch(criteria: ProductCriteria): List<Product>

    fun fetchById(productId: Long, projection: Product?): Product?

    fun fetchImage(productId: Long): ByteArray?

    fun updateImage(productId: Long, image: ByteArray): Boolean

    companion object {
        val BEAN = AtomicReference<ProductRepository>()
    }
}
