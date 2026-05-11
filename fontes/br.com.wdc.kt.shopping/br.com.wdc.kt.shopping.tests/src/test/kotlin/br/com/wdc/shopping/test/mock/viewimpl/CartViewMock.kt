package br.com.wdc.shopping.test.mock.viewimpl

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartViewState
import br.com.wdc.shopping.test.mock.ShoppingApplicationMock
import org.junit.jupiter.api.Assertions

class CartViewMock(
    app: ShoppingApplicationMock,
    presenter: CartPresenter,
) : AbstractViewMock<CartPresenter>(app, presenter) {

    companion object {
        fun cast(view: CubeView?): CartViewMock {
            Assertions.assertNotNull(view, "Expecting CartViewMock but this view was null")
            Assertions.assertInstanceOf(CartViewMock::class.java, view,
                "Expecting CartViewMock but it was ${view!!::class.simpleName}")
            return view as CartViewMock
        }
    }

    var state: CartViewState = presenter.state
}
