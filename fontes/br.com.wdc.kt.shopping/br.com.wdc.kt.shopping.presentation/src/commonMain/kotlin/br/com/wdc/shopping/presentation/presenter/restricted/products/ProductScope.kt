package br.com.wdc.shopping.presentation.presenter.restricted.products

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.Scope
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo

class ProductScope : Scope() {

    var product: ProductInfo? by field(null)
    var errorCode: Int by field(0)
    var errorMessage: String? by field(null)

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
