package br.com.wdc.shopping.domain.model

class PurchaseItem {
    var id: Long? = null
    var amount: Int? = null
    var price: Double? = null
    var purchase: Purchase? = null
    var product: Product? = null
}
