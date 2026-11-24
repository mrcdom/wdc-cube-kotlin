package br.com.wdc.shopping.test

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubePresenter
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.AbstractPresenter
import br.com.wdc.framework.cube.Scope
import br.com.wdc.framework.cube.ScopeSlot
import br.com.wdc.shopping.presentation.presenter.RootKeys
import br.com.wdc.shopping.presentation.presenter.open.login.LoginKeys
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartKeys
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductKeys
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptKeys
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.mock.viewimpl.CartViewMock
import br.com.wdc.shopping.test.mock.viewimpl.LoginViewMock
import br.com.wdc.shopping.test.mock.viewimpl.ProductViewMock
import br.com.wdc.shopping.test.mock.viewimpl.ReceiptViewMock
import br.com.wdc.shopping.test.mock.viewimpl.RestrictedViewMock
import br.com.wdc.shopping.test.util.BasePresentationTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * A reused presenter puts its view in the slot its parent offers **now**, not in the one it got when it was
 * created: the navigation reuses a presenter by place, and the parent offers the slot again on every navigation —
 * it may offer another one (a business rule, another parent, a redirect or a rollback that re-applies the route).
 */
class OwnerSlotTest : BasePresentationTest() {

    /** The live presenter whose bound scope is [scope]. */
    private fun presenterOf(scope: Scope): CubePresenter {
        var found: CubePresenter? = null
        app.forEachPresenter { if ((it as? AbstractPresenter<*>)?.scope() === scope) found = it }
        return found ?: error("no presenter for ${scope::class.simpleName}")
    }

    /** Re-applies the presenter of [scope] as a reused one (`initialization = false`) with a new owner slot. */
    private suspend fun reapplyWithANewSlot(scope: Scope, configure: (CubeIntent) -> Unit = {}): Scope? {
        var placed: Scope? = null
        val intent = app.newIntent()
        configure(intent)
        RootKeys(app, intent).parentSlot = ScopeSlot { placed = it }
        presenterOf(scope).applyParameters(intent, false, true)
        return placed
    }

    private suspend fun gotoRestricted(): RestrictedViewMock {
        LoginKeys(app).flip()
        val loginView = LoginViewMock.cast(app.getRootView()!!.contentView)
        loginView.scope.userName = "admin"
        loginView.scope.password = "admin"
        loginView.scope.onEnter(null, null)
        return RestrictedViewMock.cast(app.getRootView()!!.contentView)
    }

    @Test
    fun login_followsTheSlotOfferedNow() = runBlocking {
        LoginKeys(app).flip()
        val loginView = LoginViewMock.cast(app.getRootView()!!.contentView)
        assertSame(loginView.scope, reapplyWithANewSlot(loginView.scope))
    }

    @Test
    fun home_followsTheSlotOfferedNow() = runBlocking {
        val home = gotoRestricted()
        assertSame(home.scope, reapplyWithANewSlot(home.scope))
    }

    @Test
    fun product_followsTheSlotOfferedNow() = runBlocking {
        val home = gotoRestricted()
        home.getProductsPanelScope().onOpenProduct(DBReset.FLASH_DRIVE_2GB_ID)
        val productView = ProductViewMock.cast(RestrictedViewMock.cast(app.getRootView()!!.contentView).contentView)
        val placed = reapplyWithANewSlot(productView.scope) { ProductKeys(app, it).productId = DBReset.FLASH_DRIVE_2GB_ID }
        assertSame(productView.scope, placed)
    }

    @Test
    fun cart_followsTheSlotOfferedNow() = runBlocking {
        gotoRestricted()
        CartKeys(app).flip()
        val cartView = CartViewMock.cast(RestrictedViewMock.cast(app.getRootView()!!.contentView).contentView)
        assertSame(cartView.scope, reapplyWithANewSlot(cartView.scope))
    }

    @Test
    fun receipt_followsTheSlotOfferedNow() = runBlocking {
        val home = gotoRestricted()
        home.getPurchasesPanelScope().onOpenReceipt(DBReset.ADMIN_SECOND_PURCHASE_ID)
        val receiptView = ReceiptViewMock.cast(RestrictedViewMock.cast(app.getRootView()!!.contentView).contentView)
        val placed = reapplyWithANewSlot(receiptView.scope) { ReceiptKeys(app, it).purchaseId = DBReset.ADMIN_SECOND_PURCHASE_ID }
        assertSame(receiptView.scope, placed)
    }
}
