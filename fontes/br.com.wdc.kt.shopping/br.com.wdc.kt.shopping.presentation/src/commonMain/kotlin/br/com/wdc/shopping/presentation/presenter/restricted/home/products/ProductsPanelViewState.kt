package br.com.wdc.shopping.presentation.presenter.restricted.home.products

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.ViewState
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo

class ProductsPanelViewState : ViewState {

    var products: List<ProductInfo>? = null

    override fun write(instanceId: String, json: ExtensibleObjectOutput) {
        json.beginObject()

        json.name("id").value(instanceId)

        json.name("products").beginArray()
        for (product in products ?: emptyList()) {
            json.beginObject()
            json.name("id").value(product.id)
            json.name("image").value(product.image)
            json.name("name").value(product.name)
            json.name("description").value(product.description)
            json.name("price").value(product.price)
            json.endObject()
        }
        json.endArray()

        json.endObject()
    }
}
