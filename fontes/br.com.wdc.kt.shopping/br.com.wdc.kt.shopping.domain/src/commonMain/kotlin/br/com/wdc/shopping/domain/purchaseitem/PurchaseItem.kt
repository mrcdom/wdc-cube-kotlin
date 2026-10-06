package br.com.wdc.shopping.domain.purchaseitem

import br.com.wdc.framework.domain.codec.KeyedEntity
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.purchase.Purchase

/**
 * Item de uma compra: o produto, a quantidade e o preço praticado.
 *
 * Os campos são anuláveis porque `null` tem dois papéis: dado ausente e, numa projeção, "não traga este campo".
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
