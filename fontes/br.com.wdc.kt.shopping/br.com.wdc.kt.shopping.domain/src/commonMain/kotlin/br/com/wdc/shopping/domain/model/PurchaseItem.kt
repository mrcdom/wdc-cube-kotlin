package br.com.wdc.shopping.domain.model

import br.com.wdc.shopping.domain.product.Product

class PurchaseItem {
    var id: Long? = null
    var amount: Int? = null
    var price: Double? = null
    var purchase: Purchase? = null
    var product: Product? = null
}
