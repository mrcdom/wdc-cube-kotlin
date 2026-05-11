package br.com.wdc.shopping.presentation.presenter.restricted.products

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.ViewState
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo

class ProductViewState : ViewState {

    var product: ProductInfo? = null
    var errorCode: Int = 0
    var errorMessage: String? = null

    override fun write(instanceId: String, json: ExtensibleObjectOutput) {
        json.beginObject()

        json.name("id").value(instanceId)

        product?.let { p ->
            json.name("product").beginObject()
            json.name("id").value(p.id)
            json.name("name").value(p.name)
            json.name("description").value(p.description)
            json.name("price").value(p.price)
            json.endObject()
        }

        if (!errorMessage.isNullOrBlank()) {
            json.name("errorMessage").value(errorMessage)
        }

        json.endObject()
    }
}
