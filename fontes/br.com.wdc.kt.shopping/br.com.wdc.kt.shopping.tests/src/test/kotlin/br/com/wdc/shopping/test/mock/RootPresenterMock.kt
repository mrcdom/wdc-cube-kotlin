package br.com.wdc.shopping.test.mock

import br.com.wdc.framework.commons.storage.JvmSessionStorage
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.framework.cube.ScopeViews
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.RootScope
import br.com.wdc.shopping.presentation.presenter.open.login.LoginScope
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartItemScope
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.ProductsPanelScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.PurchasesPanelScope
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductScope
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptScope
import br.com.wdc.shopping.test.mock.viewimpl.CartItemViewMock
import br.com.wdc.shopping.test.mock.viewimpl.CartViewMock
import br.com.wdc.shopping.test.mock.viewimpl.LoginViewMock
import br.com.wdc.shopping.test.mock.viewimpl.ProductViewMock
import br.com.wdc.shopping.test.mock.viewimpl.ProductsPanelViewMock
import br.com.wdc.shopping.test.mock.viewimpl.PurchasesPanelViewMock
import br.com.wdc.shopping.test.mock.viewimpl.ReceiptViewMock
import br.com.wdc.shopping.test.mock.viewimpl.RestrictedViewMock
import br.com.wdc.shopping.test.mock.viewimpl.RootViewMock

/** [storage] is the session: a page reload is a new application on the same session. */
class RootPresenterMock(private val storage: SessionStorage = JvmSessionStorage()) : RootPresenter() {

    init {
        scopeViews = ScopeViews()
            .register<RootScope> { RootViewMock(this, it) }
            .register<LoginScope> { LoginViewMock(this, it) }
            .register<HomeScope> { RestrictedViewMock(this, it) }
            .register<CartScope> { CartViewMock(this, it) }
            .register<CartItemScope> { CartItemViewMock(this, it) }
            .register<ProductScope> { ProductViewMock(this, it) }
            .register<ReceiptScope> { ReceiptViewMock(this, it) }
            .register<ProductsPanelScope> { ProductsPanelViewMock(it) }
            .register<PurchasesPanelScope> { PurchasesPanelViewMock(it) }
    }

    override fun createSessionStorage(): SessionStorage = storage

    fun getRootView(): RootViewMock? {
        val v = view
        return if (v is RootViewMock) v else null
    }

    override fun updateHistory() {
        // NOOP
    }
}
