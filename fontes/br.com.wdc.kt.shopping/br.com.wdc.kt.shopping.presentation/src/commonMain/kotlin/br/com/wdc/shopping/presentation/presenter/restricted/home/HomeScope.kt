package br.com.wdc.shopping.presentation.presenter.restricted.home

import br.com.wdc.framework.cube.Scope
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelScope
import br.com.wdc.framework.cube.Action

class HomeScope : Scope() {

    /** The scope of the place below the home (product, cart, receipt), or none. */
    var content: Scope? by field(null)
    var productsPanel: ProductsPanelScope? by field(null)
    var purchasesPanel: PurchasesPanelScope? by field(null)
    var nickName: String? by field(null)
    var cartItemCount: Int by field(0)
    var errorCode: Int by field(0)
    var errorMessage: String? by field(null)

    var onOpenCart: Action = Action.NOOP
    var onExit: Action = Action.NOOP
}
