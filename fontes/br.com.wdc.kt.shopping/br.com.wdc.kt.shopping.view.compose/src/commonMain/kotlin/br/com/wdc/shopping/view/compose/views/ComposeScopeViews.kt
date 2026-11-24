package br.com.wdc.shopping.view.compose.views

import br.com.wdc.framework.cube.ScopeViews
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.RootScope
import br.com.wdc.shopping.presentation.presenter.open.login.LoginScope
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartItemScope
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelScope
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductScope
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptScope

/**
 * The Compose catalog of views: which view draws each scope of [app]. Every Compose entry point (web, desktop,
 * Android, iOS) assigns it to `app.scopeViews` before the first navigation, so a new screen is registered here once.
 */
fun composeScopeViews(app: RootPresenter): ScopeViews = ScopeViews()
    .register<RootScope> { RootView(app, it) }
    .register<LoginScope> { LoginView(app, it) }
    .register<HomeScope> { HomeView(app, it) }
    .register<CartScope> { CartView(app, it) }
    .register<CartItemScope> { CartItemView(app, it) }
    .register<ProductScope> { ProductView(app, it) }
    .register<ReceiptScope> { ReceiptView(app, it) }
    .register<ProductsPanelScope> { ProductsPanelView(app, it) }
    .register<PurchasesPanelScope> { PurchasesPanelView(app, it) }
