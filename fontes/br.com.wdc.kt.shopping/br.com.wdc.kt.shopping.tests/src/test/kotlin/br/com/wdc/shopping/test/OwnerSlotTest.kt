package br.com.wdc.shopping.test

import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubePresenter
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.CubeViewSlot
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

    /** Re-applies [presenter] as a reused one (`initialization = false`) with a new owner slot; returns what went in. */
    private suspend fun reapplyWithANewSlot(presenter: CubePresenter, configure: (CubeIntent) -> Unit = {}): CubeView? {
        var placed: CubeView? = null
        val intent = app.newIntent()
        configure(intent)
        RootKeys(app, intent).parentSlot = CubeViewSlot { placed = it }
        presenter.applyParameters(intent, false, true)
        return placed
    }

    private suspend fun gotoRestricted(): RestrictedViewMock {
        LoginKeys(app).flip()
        val loginView = LoginViewMock.cast(app.getRootView()!!.state.contentView)
        loginView.state.userName = "admin"
        loginView.state.password = "admin"
        loginView.presenter.onEnter()
        return RestrictedViewMock.cast(app.getRootView()!!.state.contentView)
    }

    @Test
    fun login_followsTheSlotOfferedNow() = runBlocking {
        LoginKeys(app).flip()
        val loginView = LoginViewMock.cast(app.getRootView()!!.state.contentView)
        assertSame(loginView, reapplyWithANewSlot(loginView.presenter))
    }

    @Test
    fun home_followsTheSlotOfferedNow() = runBlocking {
        val home = gotoRestricted()
        assertSame(home, reapplyWithANewSlot(home.presenter))
    }

    @Test
    fun product_followsTheSlotOfferedNow() = runBlocking {
        val home = gotoRestricted()
        home.presenter.onOpenProduct(DBReset.FLASH_DRIVE_2GB_ID)
        val productView = ProductViewMock.cast(RestrictedViewMock.cast(app.getRootView()!!.state.contentView).state.contentView)
        val placed = reapplyWithANewSlot(productView.presenter) { ProductKeys(app, it).productId = DBReset.FLASH_DRIVE_2GB_ID }
        assertSame(productView, placed)
    }

    @Test
    fun cart_followsTheSlotOfferedNow() = runBlocking {
        gotoRestricted()
        CartKeys(app).flip()
        val cartView = CartViewMock.cast(RestrictedViewMock.cast(app.getRootView()!!.state.contentView).state.contentView)
        assertSame(cartView, reapplyWithANewSlot(cartView.presenter))
    }

    @Test
    fun receipt_followsTheSlotOfferedNow() = runBlocking {
        val home = gotoRestricted()
        home.presenter.onOpenReceipt(DBReset.ADMIN_SECOND_PURCHASE_ID)
        val receiptView = ReceiptViewMock.cast(RestrictedViewMock.cast(app.getRootView()!!.state.contentView).state.contentView)
        val placed = reapplyWithANewSlot(receiptView.presenter) { ReceiptKeys(app, it).purchaseId = DBReset.ADMIN_SECOND_PURCHASE_ID }
        assertSame(receiptView, placed)
    }
}
