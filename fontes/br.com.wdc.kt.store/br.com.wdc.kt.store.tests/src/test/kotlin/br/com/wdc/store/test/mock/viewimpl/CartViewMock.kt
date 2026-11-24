package br.com.wdc.store.test.mock.viewimpl

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.store.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.store.test.mock.RootPresenterMock
import org.junit.jupiter.api.Assertions

class CartViewMock(
    app: RootPresenterMock,
    scope: CartScope,
) : AbstractViewMock<CartScope>(app, scope) {

    companion object {
        fun cast(view: CubeView?): CartViewMock {
            Assertions.assertNotNull(view, "Expecting CartViewMock but this view was null")
            Assertions.assertInstanceOf(CartViewMock::class.java, view,
                "Expecting CartViewMock but it was ${view!!::class.simpleName}")
            return view as CartViewMock
        }
    }

}
