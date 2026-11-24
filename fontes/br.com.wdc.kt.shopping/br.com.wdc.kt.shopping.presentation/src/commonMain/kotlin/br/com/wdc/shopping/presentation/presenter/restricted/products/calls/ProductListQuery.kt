package br.com.wdc.shopping.presentation.presenter.restricted.products.calls

import br.com.wdc.framework.domain.resource.ResourceQuery
import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo

/** The products by name, without their descriptions (the home's panel), at most [limit]. */
class ProductListQuery(private val limit: Int) : ResourceQuery<List<ProductInfo>>() {

    override suspend fun run(): List<ProductInfo> {
        val projection = ProductInfo.projection().apply { description = null }
        val criteria = ProductCriteria().withProjection(projection).withOrderBy(ProductCriteria.OrderBy.NAME_A_TO_Z)
        return ProductRepository.BEAN.get().fetch(criteria, limit = limit).mapNotNull { ProductInfo.create(it) }
    }
}
