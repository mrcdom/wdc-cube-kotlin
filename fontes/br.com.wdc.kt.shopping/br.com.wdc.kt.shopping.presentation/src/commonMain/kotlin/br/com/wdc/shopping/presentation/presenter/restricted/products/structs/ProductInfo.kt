package br.com.wdc.shopping.presentation.presenter.restricted.products.structs

import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.framework.domain.projection.ProjectionValues

class ProductInfo {

    var id: Long = 0
    var image: String? = null
    var name: String? = null
    var description: String? = null
    var price: Double = 0.0

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
            // não lida (fora da projeção) ou vazia no banco: fica nula, e cada view decide; não inventa texto
            item.description = product.description
            item.image = "image/product/${item.id}.png"
            return item
        }
    }
}
