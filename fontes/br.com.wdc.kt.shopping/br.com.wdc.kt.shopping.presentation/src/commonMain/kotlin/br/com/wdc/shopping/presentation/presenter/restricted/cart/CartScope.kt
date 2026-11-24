package br.com.wdc.shopping.presentation.presenter.restricted.cart

import br.com.wdc.framework.cube.Scope
import br.com.wdc.shopping.presentation.presenter.restricted.cart.structs.CartItem

class CartScope : Scope() {

    var items: List<CartItem> by field(emptyList())
    var errorCode: Int by field(0)
    var errorMessage: String? by field(null)
}
