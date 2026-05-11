package br.com.wdc.shopping.domain.criteria

import br.com.wdc.shopping.domain.model.Product

class ProductCriteria {
    var projection: Product? = null
    var offset: Int? = null
    var limit: Int? = null
    var productId: Long? = null
    var orderBy: OrderBy? = null

    fun withProjection(projection: Product?) = apply { this.projection = projection }
    fun withOffset(offset: Int?) = apply { this.offset = offset }
    fun withLimit(limit: Int?) = apply { this.limit = limit }
    fun withProductId(productId: Long?) = apply { this.productId = productId }
    fun withOrderBy(orderBy: OrderBy?) = apply { this.orderBy = orderBy }

    enum class OrderBy {
        ACENDING,
        DESCENDING
    }
}
