package br.com.wdc.shopping.presentation.presenter.restricted.home.products

import br.com.wdc.framework.cube.Scope
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo

class ProductsPanelScope : Scope() {

    var products: List<ProductInfo>? by field(null)
}
