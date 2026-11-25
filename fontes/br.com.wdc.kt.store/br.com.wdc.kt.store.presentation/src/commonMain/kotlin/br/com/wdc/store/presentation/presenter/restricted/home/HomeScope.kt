package br.com.wdc.store.presentation.presenter.restricted.home

import br.com.wdc.framework.cube.Action
import br.com.wdc.framework.cube.ActionOf
import br.com.wdc.framework.cube.Scope
import br.com.wdc.store.presentation.presenter.restricted.home.structs.PurchaseInfo
import br.com.wdc.store.presentation.presenter.restricted.products.structs.ProductInfo

/**
 * The home: its header, the place below it, and its two panels — segments of this scope, declared in this file
 * because they are parts of the same screen.
 */
class HomeScope : Scope() {

    companion object {
        /** The client component that paints this scope in a remote view: its id in the bridge protocol. */
        const val VID = "473dbdd7a36a"
    }

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

/** The home's products panel: the catalog, without descriptions. */
class ProductsPanelScope : Scope() {

    companion object {
        /** The client component that paints this scope in a remote view: its id in the bridge protocol. */
        const val VID = "a1b2c3d4e5f6"
    }

    var products: List<ProductInfo>? by field(null)

    var onOpenProduct: ActionOf<Long?> = ActionOf.noop()
}

/** The home's purchases panel: one page of the user's statement. */
class PurchasesPanelScope : Scope() {

    companion object {
        /** The client component that paints this scope in a remote view: its id in the bridge protocol. */
        const val VID = "b3c4d5e6f7a8"
    }

    var purchases: List<PurchaseInfo> by field(emptyList())
    var page: Int by field(0)
    var pageSize: Int by field(-1)
    var totalCount: Int by field(0)

    var onOpenReceipt: ActionOf<Long?> = ActionOf.noop()
    var onPageChange: ActionOf<Int?> = ActionOf.noop()
    var onItemSizeCapacityChanged: ActionOf<Int?> = ActionOf.noop()
}
