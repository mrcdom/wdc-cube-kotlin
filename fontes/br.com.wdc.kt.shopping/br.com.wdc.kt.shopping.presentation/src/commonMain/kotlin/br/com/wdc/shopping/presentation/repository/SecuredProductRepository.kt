package br.com.wdc.shopping.presentation.repository

import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.presentation.util.withSecurityContext

/**
 * Executa as operações de produto com o contexto de segurança da aplicação. `fetchById`, `fetchPage` e
 * `insertOrUpdate` vêm dos defaults da interface, que passam pelas operações abaixo.
 */
class SecuredProductRepository(
    private val delegate: ProductRepository,
    private val contextSupplier: () -> SecurityContext?,
) : ProductRepository {

    override fun newProjection(): Product = delegate.newProjection()

    override suspend fun insert(bean: Product) =
        withSecurityContext(contextSupplier) { delegate.insert(bean) }

    override suspend fun update(newBean: Product, oldBean: Product?, projection: Product?) =
        withSecurityContext(contextSupplier) { delegate.update(newBean, oldBean, projection) }

    override suspend fun delete(criteria: ProductCriteria) =
        withSecurityContext(contextSupplier) { delegate.delete(criteria) }

    override suspend fun count(criteria: ProductCriteria) =
        withSecurityContext(contextSupplier) { delegate.count(criteria) }

    override suspend fun fetch(criteria: ProductCriteria, offset: Int, limit: Int) =
        withSecurityContext(contextSupplier) { delegate.fetch(criteria, offset, limit) }

    override suspend fun fetchImage(productId: Long) =
        withSecurityContext(contextSupplier) { delegate.fetchImage(productId) }

    override suspend fun updateImage(productId: Long, image: ByteArray) =
        withSecurityContext(contextSupplier) { delegate.updateImage(productId, image) }
}
