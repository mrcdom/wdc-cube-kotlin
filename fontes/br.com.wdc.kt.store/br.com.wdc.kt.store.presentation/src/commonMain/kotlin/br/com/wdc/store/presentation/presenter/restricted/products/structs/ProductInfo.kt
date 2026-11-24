package br.com.wdc.store.presentation.presenter.restricted.products.structs

import br.com.wdc.framework.cube.ScopeValue
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.store.domain.product.Product
import br.com.wdc.framework.domain.projection.ProjectionValues

class ProductInfo : ScopeValue {

    var id: Long = 0
    var image: String? = null
    var name: String? = null
    var description: String? = null
    var price: Double = 0.0

    override fun writeJson(json: ExtensibleObjectOutput) {
        json.beginObject()
        json.name("id").value(id)
        json.name("image").value(image)
        json.name("name").value(name)
        json.name("description").value(description)
        json.name("price").value(price)
        json.endObject()
    }

    companion object {
        fun projection(): Product {
            val pv = ProjectionValues
            val prj = Product()
            prj.id = pv.i64
            prj.name = pv.str
            prj.price = pv.f64
            prj.description = pv.str
            return prj
        }

        fun create(product: Product?): ProductInfo? {
            if (product == null) return null
            val item = ProductInfo()
            item.id = product.id ?: -1L
            item.name = product.name ?: "unknown"
            item.price = product.price ?: 0.0
            // not read (outside the projection) or empty in the database: stays null, and each view decides; no made-up text
            item.description = product.description
            item.image = "image/product/${item.id}.png"
            return item
        }
    }
}
