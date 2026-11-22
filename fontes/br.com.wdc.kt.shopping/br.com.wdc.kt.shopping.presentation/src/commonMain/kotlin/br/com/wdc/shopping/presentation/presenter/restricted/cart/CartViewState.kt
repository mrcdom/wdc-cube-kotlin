package br.com.wdc.shopping.presentation.presenter.restricted.cart

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.ObservableViewState
import br.com.wdc.shopping.presentation.presenter.restricted.cart.structs.CartItem

class CartViewState : ObservableViewState() {

    var items: List<CartItem> by field(emptyList())
    var errorCode: Int by field(0)
    var errorMessage: String? by field(null)

    override fun write(instanceId: String, json: ExtensibleObjectOutput) {
        json.beginObject()

        json.name("id").value(instanceId)

        json.name("items").beginArray()
        for (item in items) {
            json.beginObject()
            json.name("id").value(item.id)
            json.name("name").value(item.name)
            json.name("price").value(item.price)
            json.name("quantity").value(item.quantity.toLong())
            json.endObject()
        }
        json.endArray()

        if (!errorMessage.isNullOrBlank()) {
            json.name("errorMessage").value(errorMessage)
        }

        json.endObject()
    }
}
