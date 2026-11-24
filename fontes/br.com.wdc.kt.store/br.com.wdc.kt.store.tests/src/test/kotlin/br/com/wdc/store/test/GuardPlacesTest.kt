package br.com.wdc.store.test

import br.com.wdc.store.presentation.presenter.open.login.LoginKeys
import br.com.wdc.store.presentation.presenter.restricted.cart.CartKeys
import br.com.wdc.store.test.mock.viewimpl.LoginViewMock
import br.com.wdc.store.test.mock.viewimpl.RestrictedViewMock
import br.com.wdc.store.test.util.BasePresentationTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The open and restricted guard places: the rule of each area, with no scope or view of their own. */
class GuardPlacesTest : BasePresentationTest() {

    private fun rootContent() = app.getRootView()!!.contentView

    private suspend fun signIn() {
        LoginKeys(app).flip()
        val login = LoginViewMock.cast(rootContent())
        login.scope.userName = "admin"
        login.scope.password = "admin"
        login.scope.onEnter(null, null)
        RestrictedViewMock.cast(rootContent())
    }

    @Test
    fun withoutAUser_aRestrictedPlace_goesToTheLogin() = runBlocking {
        CartKeys(app).flip()
        LoginViewMock.cast(rootContent())
        Unit
    }

    @Test
    fun withAUser_theLogin_goesToTheHome() = runBlocking {
        signIn()
        LoginKeys(app).flip()
        val home = RestrictedViewMock.cast(rootContent())
        assertNull(home.contentView, "the home itself, nothing inside it")
    }

    @Test
    fun theGuardsAlone_goOnToTheDefaultScreenOfTheirArea() = runBlocking {
        app.flip("restricted")
        LoginViewMock.cast(rootContent()) // no user: the restricted area sends to the open one, which shows the login
        app.flip("open")
        LoginViewMock.cast(rootContent())

        signIn()
        app.flip("restricted")
        RestrictedViewMock.cast(rootContent())
        app.flip("open")
        RestrictedViewMock.cast(rootContent()) // a signed-in user has no reason to be in the open area
        Unit
    }
}
