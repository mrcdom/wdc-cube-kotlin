package br.com.wdc.store.presentation.presenter.restricted.cart

import br.com.wdc.framework.cube.Action
import br.com.wdc.framework.cube.ActionOf
import br.com.wdc.framework.cube.Scope

class CartScope : Scope() {

    /** One row per product in the cart, each a view of its own. */
    var rows: List<CartItemScope> by scopes()

    /** The cart's total, computed here, not by the views. */
    var total: Double by field(0.0)
    var errorCode: Int by field(0)
    var errorMessage: String? by field(null)

    var onBuy: Action = Action.NOOP
    var onOpenProducts: Action = Action.NOOP
}

/** A row of the cart: one product, its quantity and its own actions. */
class CartItemScope : Scope() {

    var productId: Long by field(0L)
    var name: String? by field(null)
    var image: String? by field(null)
    var price: Double by field(0.0)
    var quantity: Int by field(0)

    var onRemove: Action = Action.NOOP
    var onModifyQuantity: ActionOf<Int?> = ActionOf.noop()
}
