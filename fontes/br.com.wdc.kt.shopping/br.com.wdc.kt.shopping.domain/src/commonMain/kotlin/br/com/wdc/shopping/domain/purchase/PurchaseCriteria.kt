package br.com.wdc.shopping.domain.purchase

import br.com.wdc.framework.domain.criteria.ComparableCriterion
import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.criteria.Criterion
import br.com.wdc.shopping.domain.model.PlatformDateTime

/**
 * Critério de pesquisa de [Purchase].
 *
 * Os campos nascem com o critério e nunca são `null`: para saber se um deles filtra, use `hasXxx()`.
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

    /** Compras que contêm o produto — não é coluna da compra: resolve-se pelos itens. */
    val productId = ComparableCriterion<PurchaseCriteria, Long>(this, "productId")
    fun hasProductId(): Boolean = productId.isSet()
    fun withProductId(value: Long?): PurchaseCriteria = if (value == null) this else productId.eq(value)

    override fun criterions(): List<Criterion<*, *>> = listOf(purchaseId, userId, buyDate, productId)

    var orderBy: OrderBy? = null
        private set

    fun withOrderBy(orderBy: OrderBy?) = apply { this.orderBy = orderBy }

    /** Cada ordenação nomeia um efeito e tem um índice que a sustenta (ver `DBCreate`). */
    enum class OrderBy {
        /** Pela ordem de cadastro. */
        OLDEST_FIRST,
        NEWEST_FIRST,

        /** Pela data da compra — é a ordem de um extrato. */
        MOST_RECENT_PURCHASE_FIRST,
        EARLIEST_PURCHASE_FIRST,
    }
}
