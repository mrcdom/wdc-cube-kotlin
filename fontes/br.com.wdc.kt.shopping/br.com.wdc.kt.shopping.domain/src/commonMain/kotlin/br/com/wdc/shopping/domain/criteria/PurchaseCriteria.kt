package br.com.wdc.shopping.domain.criteria

import br.com.wdc.shopping.domain.model.Purchase

class PurchaseCriteria {
    var projection: Purchase? = null
    var offset: Int? = null
    var limit: Int? = null
    var purchaseId: Long? = null
    var userId: Long? = null
    var orderBy: OrderBy? = null

    fun withProjection(projection: Purchase?) = apply { this.projection = projection }
    fun withOffset(offset: Int?) = apply { this.offset = offset }
    fun withLimit(limit: Int?) = apply { this.limit = limit }
    fun withPurchaseId(purchaseId: Long?) = apply { this.purchaseId = purchaseId }
    fun withUserId(userId: Long?) = apply { this.userId = userId }
    fun withOrderBy(orderBy: OrderBy?) = apply { this.orderBy = orderBy }

    enum class OrderBy {
        ASCENDING,
        DESCENDING
    }
}
