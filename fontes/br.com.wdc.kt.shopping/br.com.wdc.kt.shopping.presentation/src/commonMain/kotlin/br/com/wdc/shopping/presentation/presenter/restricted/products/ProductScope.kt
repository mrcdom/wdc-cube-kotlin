package br.com.wdc.shopping.presentation.presenter.restricted.products

import br.com.wdc.framework.cube.Scope
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo

class ProductScope : Scope() {

    var product: ProductInfo? by field(null)
    var errorCode: Int by field(0)
    var errorMessage: String? by field(null)
}
