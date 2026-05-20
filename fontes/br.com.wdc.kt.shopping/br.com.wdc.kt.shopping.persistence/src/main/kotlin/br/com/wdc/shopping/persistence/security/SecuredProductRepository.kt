package br.com.wdc.shopping.persistence.security

import br.com.wdc.shopping.domain.criteria.ProductCriteria
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.repositories.Page
import br.com.wdc.shopping.domain.repositories.ProductRepository

class SecuredProductRepository(private val delegate: ProductRepository) : ProductRepository {

    companion object {
        private const val ENTITY = "product"
    }

    override fun insert(product: Product): Boolean {
        SecurityEnforcer.require(ENTITY, "write")
        return delegate.insert(product)
    }

    override fun update(newProduct: Product, oldProduct: Product): Boolean {
        SecurityEnforcer.require(ENTITY, "write")
        return delegate.update(newProduct, oldProduct)
    }

    override fun insertOrUpdate(product: Product): Boolean {
        SecurityEnforcer.require(ENTITY, "write")
        return delegate.insertOrUpdate(product)
    }

    override fun delete(criteria: ProductCriteria): Int {
        SecurityEnforcer.require(ENTITY, "delete")
        return delegate.delete(criteria)
    }

    override fun count(criteria: ProductCriteria): Int {
        SecurityEnforcer.require(ENTITY, "read")
        return delegate.count(criteria)
    }

    override fun fetch(criteria: ProductCriteria): List<Product> {
        SecurityEnforcer.require(ENTITY, "read")
        return delegate.fetch(criteria)
    }

    override fun fetchPage(criteria: ProductCriteria): Page<Product> {
        SecurityEnforcer.require(ENTITY, "read")
        return delegate.fetchPage(criteria)
    }

    override fun fetchById(productId: Long, projection: Product?): Product? {
        SecurityEnforcer.require(ENTITY, "read")
        return delegate.fetchById(productId, projection)
    }

    override fun fetchImage(productId: Long): ByteArray? = delegate.fetchImage(productId)

    override fun updateImage(productId: Long, image: ByteArray): Boolean {
        SecurityEnforcer.require(ENTITY, "write")
        return delegate.updateImage(productId, image)
    }
}
