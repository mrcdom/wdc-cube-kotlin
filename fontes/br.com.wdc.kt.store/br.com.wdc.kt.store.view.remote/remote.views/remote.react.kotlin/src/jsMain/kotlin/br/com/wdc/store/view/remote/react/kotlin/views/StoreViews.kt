package br.com.wdc.store.view.remote.react.kotlin.views

import br.com.wdc.framework.cube.remote.bridge.RemoteBridge
import br.com.wdc.framework.cube.remote.bridge.react.RemoteViews
import br.com.wdc.framework.cube.remote.bridge.react.RemoteViewsContext
import br.com.wdc.store.view.remote.react.kotlin.views.home.HomeView
import br.com.wdc.store.view.remote.react.kotlin.views.home.ProductsPanelView
import br.com.wdc.store.view.remote.react.kotlin.views.home.PurchasesPanelView
import react.useRequired

/** The vids of the Store's scopes: the server's `XxxScope.VID`, the protocol's names for each screen. */
object StoreVids {
    const val ROOT = "f2d345c4a610"
    const val LOGIN = "c677cda52d14"
    const val HOME = "473dbdd7a36a"
    const val PRODUCTS_PANEL = "a1b2c3d4e5f6"
    const val PURCHASES_PANEL = "b3c4d5e6f7a8"
    const val PRODUCT = "48b693f67410"
    const val CART = "7eb485e5f843"
    const val CART_ITEM = "5d9e3c7a1f42"
    const val RECEIPT = "e8d0bd8ae3bc"
}

/** Registers the component of each Store vid, the browser's included. */
fun RemoteViews.registerStoreViews(): RemoteViews = this
    .register(RemoteBridge.BROWSER_VID, BrowserView)
    .register(StoreVids.ROOT, RootView)
    .register(StoreVids.LOGIN, LoginView)
    .register(StoreVids.HOME, HomeView)
    .register(StoreVids.PRODUCTS_PANEL, ProductsPanelView)
    .register(StoreVids.PURCHASES_PANEL, PurchasesPanelView)
    .register(StoreVids.PRODUCT, ProductView)
    .register(StoreVids.CART, CartView)
    .register(StoreVids.CART_ITEM, CartItemView)
    .register(StoreVids.RECEIPT, ReceiptView)

/** The bridge of the views of the context. */
fun useBridge(): RemoteBridge = useRequired(RemoteViewsContext).bridge
