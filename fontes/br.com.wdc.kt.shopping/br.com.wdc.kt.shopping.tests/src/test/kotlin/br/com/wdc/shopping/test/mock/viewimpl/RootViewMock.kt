package br.com.wdc.shopping.test.mock.viewimpl

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.RootViewState
import br.com.wdc.shopping.test.mock.ShoppingApplicationMock
import org.junit.jupiter.api.Assertions

class RootViewMock(
    app: ShoppingApplicationMock,
    presenter: RootPresenter,
) : AbstractViewMock<RootPresenter>(app, presenter) {

    companion object {
        fun cast(view: CubeView?): RootViewMock {
            Assertions.assertNotNull(view, "Expecting RootViewMock but this view was null")
            Assertions.assertInstanceOf(RootViewMock::class.java, view,
                "Expecting RootViewMock but it was ${view!!::class.simpleName}")
            return view as RootViewMock
        }
    }

    var state: RootViewState = presenter.state
}
