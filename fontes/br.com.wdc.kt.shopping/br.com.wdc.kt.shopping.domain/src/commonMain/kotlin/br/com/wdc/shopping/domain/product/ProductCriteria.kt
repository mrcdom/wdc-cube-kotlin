package br.com.wdc.shopping.domain.product

import br.com.wdc.framework.domain.criteria.ComparableCriterion
import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.criteria.Criterion
import br.com.wdc.framework.domain.criteria.TextCriterion

/**
 * Search criteria for [Product].
 *
 * The fields are created with the criteria and are never `null`: to know whether one filters, use `hasXxx()` —
 * `criteria.productId == null` compiles and is always false.
 */
class ProductCriteria : Criteria {

    var projection: Product? = null
        private set

    fun withProjection(projection: Product?) = apply { this.projection = projection }

    val productId = ComparableCriterion<ProductCriteria, Long>(this, "productId")
    fun hasProductId(): Boolean = productId.isSet()
    fun withProductId(value: Long?): ProductCriteria = if (value == null) this else productId.eq(value)

    val name = TextCriterion(this, "name")
    fun hasName(): Boolean = name.isSet()
    fun withName(value: String?): ProductCriteria = if (value == null) this else name.eq(value)

    val price = ComparableCriterion<ProductCriteria, Double>(this, "price")
    fun hasPrice(): Boolean = price.isSet()
    fun withPrice(value: Double?): ProductCriteria = if (value == null) this else price.eq(value)

    val description = TextCriterion(this, "description")
    fun hasDescription(): Boolean = description.isSet()
    fun withDescription(value: String?): ProductCriteria = if (value == null) this else description.eq(value)

    override fun criterions(): List<Criterion<*, *>> = listOf(productId, name, price, description)

    /**
     * The alternative groups (see [Criteria.anyOf]): server-side only — the REST data policies restrict the rows a
     * role reaches with them, and the codec refuses them on the wire.
     */
    override var anyOf: List<ProductCriteria> = emptyList()
        private set

    /** Replaces the alternative groups. */
    fun withAnyOf(groups: List<ProductCriteria>?) = apply { anyOf = groups?.toList() ?: emptyList() }

    /** Replaces the alternative groups. */
    fun withAnyOf(vararg groups: ProductCriteria) = withAnyOf(groups.asList())

    var orderBy: OrderBy? = null
        private set

    fun withOrderBy(orderBy: OrderBy?) = apply { this.orderBy = orderBy }

    /** Each ordering names an effect and has an index backing it (see `DBCreate`). */
    enum class OrderBy {
        OLDEST_FIRST,
        NEWEST_FIRST,
        NAME_A_TO_Z,
        CHEAPEST_FIRST,
        MOST_EXPENSIVE_FIRST,
    }
}
