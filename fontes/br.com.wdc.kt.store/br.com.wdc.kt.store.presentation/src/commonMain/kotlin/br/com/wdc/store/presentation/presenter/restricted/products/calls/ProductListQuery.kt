package br.com.wdc.store.presentation.presenter.restricted.products.calls

import br.com.wdc.framework.domain.resource.ResourceQuery
import br.com.wdc.store.domain.product.ProductCriteria
import br.com.wdc.store.domain.product.ProductRepository
import br.com.wdc.store.presentation.presenter.restricted.products.structs.ProductInfo

/** The products by name, without their descriptions (the home's panel), at most [limit]. */
class ProductListQuery(private val limit: Int) : ResourceQuery<List<ProductInfo>>() {

    private val productRepo: ProductRepository get() = ProductRepository.BEAN.get()

    override suspend fun run(): List<ProductInfo> {
        val projection = ProductInfo.projection().apply { description = null }
        val criteria = ProductCriteria().withProjection(projection).withOrderBy(ProductCriteria.OrderBy.NAME_A_TO_Z)
        return productRepo.fetch(criteria, limit = limit).mapNotNull { ProductInfo.create(it) }
    }
}
