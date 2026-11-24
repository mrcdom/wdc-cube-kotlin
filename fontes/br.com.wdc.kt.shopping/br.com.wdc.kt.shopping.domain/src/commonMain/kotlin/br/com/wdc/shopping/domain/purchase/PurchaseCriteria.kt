package br.com.wdc.shopping.domain.purchase

import br.com.wdc.framework.domain.criteria.ComparableCriterion
import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.criteria.Criterion
import br.com.wdc.shopping.domain.model.PlatformDateTime

/**
 * Search criteria for [Purchase].
 *
 * The fields are created with the criteria and are never `null`: to know whether one filters, use `hasXxx()`.
 */
class PurchaseCriteria : Criteria {

    var projection: Purchase? = null
        private set

    fun withProjection(projection: Purchase?) = apply { this.projection = projection }

    val purchaseId = ComparableCriterion<PurchaseCriteria, Long>(this, "purchaseId")
    fun hasPurchaseId(): Boolean = purchaseId.isSet()
    fun withPurchaseId(value: Long?): PurchaseCriteria = if (value == null) this else purchaseId.eq(value)

    val userId = ComparableCriterion<PurchaseCriteria, Long>(this, "userId")
    fun hasUserId(): Boolean = userId.isSet()
    fun withUserId(value: Long?): PurchaseCriteria = if (value == null) this else userId.eq(value)

    val buyDate = ComparableCriterion<PurchaseCriteria, PlatformDateTime>(this, "buyDate")
    fun hasBuyDate(): Boolean = buyDate.isSet()
    fun withBuyDate(value: PlatformDateTime?): PurchaseCriteria = if (value == null) this else buyDate.eq(value)

    /** Purchases containing the product — not a purchase column: resolved through the items. */
    val productId = ComparableCriterion<PurchaseCriteria, Long>(this, "productId")
    fun hasProductId(): Boolean = productId.isSet()
    fun withProductId(value: Long?): PurchaseCriteria = if (value == null) this else productId.eq(value)

    override fun criterions(): List<Criterion<*, *>> = listOf(purchaseId, userId, buyDate, productId)

    /**
     * The alternative groups (see [Criteria.anyOf]): server-side only — the REST data policies restrict the rows a
     * role reaches with them, and the codec refuses them on the wire.
     */
    override var anyOf: List<PurchaseCriteria> = emptyList()
        private set

    /** Replaces the alternative groups. */
    fun withAnyOf(groups: List<PurchaseCriteria>?) = apply { anyOf = groups?.toList() ?: emptyList() }

    /** Replaces the alternative groups. */
    fun withAnyOf(vararg groups: PurchaseCriteria) = withAnyOf(groups.asList())

    var orderBy: OrderBy? = null
        private set

    fun withOrderBy(orderBy: OrderBy?) = apply { this.orderBy = orderBy }

    /** Each ordering names an effect and has an index backing it (see `DBCreate`). */
    enum class OrderBy {
        /** By insertion order. */
        OLDEST_FIRST,
        NEWEST_FIRST,

        /** By purchase date — the order of a statement. */
        MOST_RECENT_PURCHASE_FIRST,
        EARLIEST_PURCHASE_FIRST,
    }
}
