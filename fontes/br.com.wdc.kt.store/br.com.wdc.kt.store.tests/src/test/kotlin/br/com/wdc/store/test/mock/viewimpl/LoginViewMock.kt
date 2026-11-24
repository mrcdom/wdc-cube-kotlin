package br.com.wdc.store.test.mock.viewimpl

import br.com.wdc.store.presentation.presenter.open.login.LoginScope
import br.com.wdc.store.test.mock.RootPresenterMock
import org.junit.jupiter.api.Assertions

class LoginViewMock(
    app: RootPresenterMock,
    scope: LoginScope,
) : AbstractViewMock<LoginScope>(app, scope) {

    companion object {
        fun cast(view: AbstractViewMock<*>?): LoginViewMock {
            Assertions.assertNotNull(view, "Expecting LoginViewMock but this view was null")
            Assertions.assertInstanceOf(LoginViewMock::class.java, view,
                "Expecting LoginViewMock but it was ${view!!::class.simpleName}")
            return view as LoginViewMock
        }
    }

}
