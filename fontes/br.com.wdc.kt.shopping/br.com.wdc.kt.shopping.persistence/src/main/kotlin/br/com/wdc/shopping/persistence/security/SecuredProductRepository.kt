package br.com.wdc.shopping.persistence.security

import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.domain.product.ProductRepository

/**
 * Aplica o controle de acesso a produto. `fetchById`, `fetchPage` e `insertOrUpdate` não são sobrescritos de
 * propósito: os defaults da interface passam por `fetch`, `count`, `insert` e `update`, que já são verificados.
 */
class SecuredProductRepository(private val delegate: ProductRepository) : ProductRepository {

    companion object {
        private const val ENTITY = "product"
    }

    override fun newProjection(): Product = delegate.newProjection()

    override suspend fun insert(bean: Product): Boolean {
        SecurityEnforcer.require(ENTITY, "write")
        return delegate.insert(bean)
    }

    override suspend fun update(newBean: Product, oldBean: Product?, projection: Product?): Boolean {
        SecurityEnforcer.require(ENTITY, "write")
        return delegate.update(newBean, oldBean, projection)
    }

    override suspend fun delete(criteria: ProductCriteria): Int {
        SecurityEnforcer.require(ENTITY, "delete")
        return delegate.delete(criteria)
    }

    override suspend fun count(criteria: ProductCriteria): Int {
        SecurityEnforcer.require(ENTITY, "read")
        return delegate.count(criteria)
    }

    override suspend fun fetch(criteria: ProductCriteria, offset: Int, limit: Int): List<Product> {
        SecurityEnforcer.require(ENTITY, "read")
        return delegate.fetch(criteria, offset, limit)
    }

    /** A imagem é pública: faz parte do catálogo. */
    override suspend fun fetchImage(productId: Long): ByteArray? = delegate.fetchImage(productId)

    override suspend fun updateImage(productId: Long, image: ByteArray): Boolean {
        SecurityEnforcer.require(ENTITY, "write")
        return delegate.updateImage(productId, image)
    }
}
