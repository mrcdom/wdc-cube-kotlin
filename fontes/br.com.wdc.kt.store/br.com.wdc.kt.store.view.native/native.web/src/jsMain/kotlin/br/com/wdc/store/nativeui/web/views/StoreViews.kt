package br.com.wdc.store.nativeui.web.views

import br.com.wdc.store.nativeui.web.bridge.ScopeComponents
import br.com.wdc.store.presentation.presenter.RootScope
import br.com.wdc.store.presentation.presenter.open.login.LoginScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartItemScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.store.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.store.presentation.presenter.restricted.home.ProductsPanelScope
import br.com.wdc.store.presentation.presenter.restricted.home.PurchasesPanelScope
import br.com.wdc.store.presentation.presenter.restricted.products.ProductScope
import br.com.wdc.store.presentation.presenter.restricted.receipt.ReceiptScope

/** The native web catalog of the WDC Store: which component draws each scope. A new screen is registered here once. */
val storeViews: ScopeComponents = ScopeComponents()
    .register(RootScope::class, RootView)
    .register(LoginScope::class, LoginView)
    .register(HomeScope::class, HomeView)
    .register(CartScope::class, CartView)
    .register(CartItemScope::class, CartItemView)
    .register(ProductScope::class, ProductView)
    .register(ReceiptScope::class, ReceiptView)
    .register(ProductsPanelScope::class, ProductsPanelView)
    .register(PurchasesPanelScope::class, PurchasesPanelView)
