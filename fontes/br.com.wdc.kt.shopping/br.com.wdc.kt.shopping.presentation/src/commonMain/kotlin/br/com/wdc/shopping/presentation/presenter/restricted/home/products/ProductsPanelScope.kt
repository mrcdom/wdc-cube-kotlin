package br.com.wdc.shopping.presentation.presenter.restricted.home.products

import br.com.wdc.framework.cube.Scope
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo
import br.com.wdc.framework.cube.ActionOf

class ProductsPanelScope : Scope() {

    var products: List<ProductInfo>? by field(null)

    var onOpenProduct: ActionOf<Long?> = ActionOf.noop()
}
