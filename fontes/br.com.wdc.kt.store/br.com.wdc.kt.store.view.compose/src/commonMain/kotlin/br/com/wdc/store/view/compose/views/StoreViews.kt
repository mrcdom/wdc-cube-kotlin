package br.com.wdc.store.view.compose.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.RootScope
import br.com.wdc.store.presentation.presenter.open.login.LoginScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartItemScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.store.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.store.presentation.presenter.restricted.home.ProductsPanelScope
import br.com.wdc.store.presentation.presenter.restricted.home.PurchasesPanelScope
import br.com.wdc.store.presentation.presenter.restricted.products.ProductScope
import br.com.wdc.store.presentation.presenter.restricted.receipt.ReceiptScope
import br.com.wdc.store.view.compose.bridge.LocalScopeComposables
import br.com.wdc.store.view.compose.bridge.RenderSlot
import br.com.wdc.store.view.compose.bridge.ScopeComposables

/** The Compose catalog of the WDC Store: which view draws each scope. A new screen is registered here once. */
val storeViews: ScopeComposables = ScopeComposables()
    .register<RootScope> { RootView(it) }
    .register<LoginScope> { LoginView(it) }
    .register<HomeScope> { HomeView(it) }
    .register<CartScope> { CartView(it) }
    .register<CartItemScope> { CartItemView(it) }
    .register<ProductScope> { ProductView(it) }
    .register<ReceiptScope> { ReceiptView(it) }
    .register<ProductsPanelScope> { ProductsPanelView(it) }
    .register<PurchasesPanelScope> { PurchasesPanelView(it) }

/** The whole application: its root scope, drawn with [storeViews]. Every Compose entry point (web, desktop, Android, iOS) shows it. */
@Composable
fun StoreApp(app: RootPresenter) {
    CompositionLocalProvider(LocalScopeComposables provides storeViews) {
        RenderSlot(app.scope)
    }
}
