package br.com.wdc.store.presentation.presenter.restricted.cart.structs

import br.com.wdc.framework.cube.ScopeValue
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.store.presentation.presenter.restricted.products.structs.ProductInfo

class CartItem : ScopeValue {

    var id: Long = 0
    var image: String? = null
    var name: String? = null
    var price: Double = 0.0
    var quantity: Int = 0

    fun copy(): CartItem = CartItem().also {
        it.id = id
        it.image = image
        it.name = name
        it.price = price
        it.quantity = quantity
    }

    override fun writeJson(json: ExtensibleObjectOutput) {
        json.beginObject()
        json.name("id").value(id)
        json.name("name").value(name)
        json.name("price").value(price)
        json.name("quantity").value(quantity.toLong())
        json.endObject()
    }

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
