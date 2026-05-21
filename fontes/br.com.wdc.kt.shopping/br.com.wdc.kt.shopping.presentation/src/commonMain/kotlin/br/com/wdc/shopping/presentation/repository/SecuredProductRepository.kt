package br.com.wdc.shopping.presentation.repository

import br.com.wdc.shopping.domain.criteria.ProductCriteria
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.repositories.Page
import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.presentation.util.withSecurityContext

class SecuredProductRepository(
    private val delegate: ProductRepository,
    private val contextSupplier: () -> SecurityContext?,
) : ProductRepository {

    override suspend fun insert(product: Product) =
        withSecurityContext(contextSupplier) { delegate.insert(product) }

    override suspend fun update(newProduct: Product, oldProduct: Product) =
        withSecurityContext(contextSupplier) { delegate.update(newProduct, oldProduct) }

    override suspend fun insertOrUpdate(product: Product) =
        withSecurityContext(contextSupplier) { delegate.insertOrUpdate(product) }

    override suspend fun delete(criteria: ProductCriteria) =
        withSecurityContext(contextSupplier) { delegate.delete(criteria) }

    override suspend fun count(criteria: ProductCriteria) =
        withSecurityContext(contextSupplier) { delegate.count(criteria) }

    override suspend fun fetch(criteria: ProductCriteria) =
        withSecurityContext(contextSupplier) { delegate.fetch(criteria) }

    override suspend fun fetchPage(criteria: ProductCriteria) =
        withSecurityContext(contextSupplier) { delegate.fetchPage(criteria) }

    override suspend fun fetchById(productId: Long, projection: Product?) =
        withSecurityContext(contextSupplier) { delegate.fetchById(productId, projection) }

    override suspend fun fetchImage(productId: Long) =
        withSecurityContext(contextSupplier) { delegate.fetchImage(productId) }

    override suspend fun updateImage(productId: Long, image: ByteArray) =
        withSecurityContext(contextSupplier) { delegate.updateImage(productId, image) }
}
