package br.com.wdc.store.presentation.presenter.restricted.products

import br.com.wdc.framework.cube.Scope
import br.com.wdc.store.presentation.presenter.restricted.products.structs.ProductInfo
import br.com.wdc.framework.cube.Action
import br.com.wdc.framework.cube.ActionOf

class ProductScope : Scope() {

    companion object {
        /** The client component that paints this scope in a remote view: its id in the bridge protocol. */
        const val VID = "48b693f67410"
    }

    var product: ProductInfo? by field(null)
    var errorCode: Int by field(0)
    var errorMessage: String? by field(null)

    var onAddToCart: ActionOf<Int?> = ActionOf.noop()
    var onOpenProducts: Action = Action.NOOP
}
