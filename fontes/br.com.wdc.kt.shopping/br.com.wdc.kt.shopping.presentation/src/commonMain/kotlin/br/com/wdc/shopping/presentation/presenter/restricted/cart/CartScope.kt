package br.com.wdc.shopping.presentation.presenter.restricted.cart

import br.com.wdc.framework.cube.Action
import br.com.wdc.framework.cube.ActionOf
import br.com.wdc.framework.cube.ActionOf2
import br.com.wdc.framework.cube.Scope
import br.com.wdc.shopping.presentation.presenter.restricted.cart.structs.CartItem

class CartScope : Scope() {

    var items: List<CartItem> by field(emptyList())
    var errorCode: Int by field(0)
    var errorMessage: String? by field(null)

    var onModifyQuantity: ActionOf2<Long?, Int?> = ActionOf2.noop()
    var onRemoveProduct: ActionOf<Long?> = ActionOf.noop()
    var onBuy: Action = Action.NOOP
    var onOpenProducts: Action = Action.NOOP
}
