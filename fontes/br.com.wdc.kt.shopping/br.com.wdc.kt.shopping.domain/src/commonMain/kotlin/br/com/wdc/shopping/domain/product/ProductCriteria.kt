package br.com.wdc.shopping.domain.product

import br.com.wdc.framework.domain.criteria.ComparableCriterion
import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.criteria.Criterion
import br.com.wdc.framework.domain.criteria.TextCriterion

/**
 * Critério de pesquisa de [Product].
 *
 * Os campos nascem com o critério e nunca são `null`: para saber se um deles filtra, use `hasXxx()` —
 * `criteria.productId == null` compila e é sempre falso.
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

    var orderBy: OrderBy? = null
        private set

    fun withOrderBy(orderBy: OrderBy?) = apply { this.orderBy = orderBy }

    /** Cada ordenação nomeia um efeito e tem um índice que a sustenta (ver `DBCreate`). */
    enum class OrderBy {
        OLDEST_FIRST,
        NEWEST_FIRST,
        NAME_A_TO_Z,
        CHEAPEST_FIRST,
        MOST_EXPENSIVE_FIRST,
    }
}
