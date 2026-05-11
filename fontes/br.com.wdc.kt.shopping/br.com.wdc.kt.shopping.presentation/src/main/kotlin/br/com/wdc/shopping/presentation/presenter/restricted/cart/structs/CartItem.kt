package br.com.wdc.shopping.presentation.presenter.restricted.cart.structs

import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo

class CartItem {

    var id: Long = 0
    var image: String? = null
    var name: String? = null
    var price: Double = 0.0
    var quantity: Int = 0

    companion object {
        fun create(product: ProductInfo, quantity: Int): CartItem {
            val item = CartItem()
            item.id = product.id
            item.name = product.name
            item.image = product.image
            item.price = product.price
            item.quantity = quantity
            return item
        }
    }
}
