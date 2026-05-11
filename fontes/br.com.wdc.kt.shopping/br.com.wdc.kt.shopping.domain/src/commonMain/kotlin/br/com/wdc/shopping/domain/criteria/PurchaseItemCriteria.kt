package br.com.wdc.shopping.domain.criteria

import br.com.wdc.shopping.domain.model.PurchaseItem

class PurchaseItemCriteria {
    var projection: PurchaseItem? = null
    var offset: Int? = null
    var limit: Int? = null
    var purchaseItemId: Long? = null
    var purchaseId: Long? = null
    var productId: Long? = null
    var userId: Long? = null
    var orderBy: OrderBy? = null

    fun withProjection(projection: PurchaseItem?) = apply { this.projection = projection }
    fun withOffset(offset: Int?) = apply { this.offset = offset }
    fun withLimit(limit: Int?) = apply { this.limit = limit }
    fun withPurchaseItemId(purchaseItemId: Long?) = apply { this.purchaseItemId = purchaseItemId }
    fun withPurchaseId(purchaseId: Long?) = apply { this.purchaseId = purchaseId }
    fun withProductId(productId: Long?) = apply { this.productId = productId }
    fun withUserId(userId: Long?) = apply { this.userId = userId }
    fun withOrderBy(orderBy: OrderBy?) = apply { this.orderBy = orderBy }

    enum class OrderBy {
        ACENDING,
        DESCENDING
    }
}
