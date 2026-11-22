package br.com.wdc.shopping.domain.purchaseitem

import br.com.wdc.framework.domain.criteria.ComparableCriterion
import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.criteria.Criterion

/**
 * Search criteria for [PurchaseItem].
 *
 * The fields are created with the criteria and are never `null`: to know whether one filters, use `hasXxx()`.
 */
class PurchaseItemCriteria : Criteria {

    var projection: PurchaseItem? = null
        private set

    fun withProjection(projection: PurchaseItem?) = apply { this.projection = projection }

    val purchaseItemId = ComparableCriterion<PurchaseItemCriteria, Long>(this, "purchaseItemId")
    fun hasPurchaseItemId(): Boolean = purchaseItemId.isSet()
    fun withPurchaseItemId(value: Long?): PurchaseItemCriteria = if (value == null) this else purchaseItemId.eq(value)

    val purchaseId = ComparableCriterion<PurchaseItemCriteria, Long>(this, "purchaseId")
    fun hasPurchaseId(): Boolean = purchaseId.isSet()
    fun withPurchaseId(value: Long?): PurchaseItemCriteria = if (value == null) this else purchaseId.eq(value)

    val productId = ComparableCriterion<PurchaseItemCriteria, Long>(this, "productId")
    fun hasProductId(): Boolean = productId.isSet()
    fun withProductId(value: Long?): PurchaseItemCriteria = if (value == null) this else productId.eq(value)

    val amount = ComparableCriterion<PurchaseItemCriteria, Int>(this, "amount")
    fun hasAmount(): Boolean = amount.isSet()
    fun withAmount(value: Int?): PurchaseItemCriteria = if (value == null) this else amount.eq(value)

    val price = ComparableCriterion<PurchaseItemCriteria, Double>(this, "price")
    fun hasPrice(): Boolean = price.isSet()
    fun withPrice(value: Double?): PurchaseItemCriteria = if (value == null) this else price.eq(value)

    /** Items of the user's purchases — not an item column: resolved through the purchase. */
    val userId = ComparableCriterion<PurchaseItemCriteria, Long>(this, "userId")
    fun hasUserId(): Boolean = userId.isSet()
    fun withUserId(value: Long?): PurchaseItemCriteria = if (value == null) this else userId.eq(value)

    override fun criterions(): List<Criterion<*, *>> = listOf(purchaseItemId, purchaseId, productId, amount, price, userId)

    var orderBy: OrderBy? = null
        private set

    fun withOrderBy(orderBy: OrderBy?) = apply { this.orderBy = orderBy }

    /** Each ordering names an effect and has an index backing it (see `DBCreate`). */
    enum class OrderBy {
        OLDEST_FIRST,
        NEWEST_FIRST,
        MOST_EXPENSIVE_FIRST,
        LARGEST_QUANTITY_FIRST,
    }
}
