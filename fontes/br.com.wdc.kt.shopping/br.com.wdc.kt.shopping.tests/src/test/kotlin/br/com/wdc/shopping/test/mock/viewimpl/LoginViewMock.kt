package br.com.wdc.shopping.test.mock.viewimpl

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.presenter.open.login.LoginPresenter
import br.com.wdc.shopping.presentation.presenter.open.login.LoginViewState
import br.com.wdc.shopping.test.mock.ShoppingApplicationMock
import org.junit.jupiter.api.Assertions

class LoginViewMock(
    app: ShoppingApplicationMock,
    presenter: LoginPresenter,
) : AbstractViewMock<LoginPresenter>(app, presenter) {

    companion object {
        fun cast(view: CubeView?): LoginViewMock {
            Assertions.assertNotNull(view, "Expecting LoginViewMock but this view was null")
            Assertions.assertInstanceOf(LoginViewMock::class.java, view,
                "Expecting LoginViewMock but it was ${view!!::class.simpleName}")
            return view as LoginViewMock
        }
    }

    var state: LoginViewState = presenter.state
}
