package br.com.wdc.store.domain.purchaseitem

import br.com.wdc.framework.domain.codec.KeyedEntity
import br.com.wdc.store.domain.product.Product
import br.com.wdc.store.domain.purchase.Purchase

/**
 * A purchase item: the product, the quantity and the price charged.
 *
 * The fields are nullable because `null` has two roles: missing data and, in a projection, "do not fetch this field".
 */
class PurchaseItem : KeyedEntity {
    var id: Long? = null
    var amount: Int? = null
    var price: Double? = null
    var purchase: Purchase? = null
    var product: Product? = null

    val purchaseId: Long? get() = purchase?.id
    val productId: Long? get() = product?.id

    override fun key(): Any? = id
}
