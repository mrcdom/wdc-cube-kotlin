package br.com.wdc.store.nativeui.ios

import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.framework.commons.concurrent.IosScheduledExecutor
import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.cube.ScopeViews
import br.com.wdc.store.domain.security.IosCryptoProvider
import br.com.wdc.store.nativeui.ios.theme.StoreColors
import br.com.wdc.store.nativeui.ios.toolkit.AbstractViewIos
import br.com.wdc.store.nativeui.ios.toolkit.ViewUtils
import br.com.wdc.store.nativeui.ios.views.*
import br.com.wdc.store.persistence.client.IosHttpTransport
import br.com.wdc.store.persistence.client.RestConfig
import br.com.wdc.store.persistence.client.RestRepositoryBootstrap
import br.com.wdc.store.presentation.presenter.RootKeys
import br.com.wdc.store.presentation.presenter.RootScope
import br.com.wdc.store.presentation.presenter.open.login.LoginScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartItemScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.store.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.store.presentation.presenter.restricted.home.ProductsPanelScope
import br.com.wdc.store.presentation.presenter.restricted.home.PurchasesPanelScope
import br.com.wdc.store.presentation.presenter.restricted.products.ProductScope
import br.com.wdc.store.presentation.presenter.restricted.receipt.ReceiptScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSThread
import platform.UIKit.*

/**
 * Initializes platform services (serialization, HTTP transport, repositories).
 */
private fun initializePlatform(baseUrl: String) {
    JsonInputFactory.installCommon()
    JsonOutputFactory.installCommon()
    ScheduledExecutor.BEAN.set(IosScheduledExecutor())

    val transport = IosHttpTransport(baseUrl)
    val config = RestConfig(transport)
    RestRepositoryBootstrap.initialize(config, IosCryptoProvider())
}

/**
 * Creates the main UIViewController for the iOS native Store app.
 * Call from Swift: AppBootstrapKt.createRootViewController(baseUrl: "http://...")
 */
@OptIn(ExperimentalForeignApi::class)
fun createRootViewController(baseUrl: String): UIViewController {
    // Initialize platform services
    initializePlatform(baseUrl)

    // Configure view utilities
    ViewUtils.baseUrl = baseUrl

    // Create the application and its view catalog (before the first navigation)
    val app = IosNativeRootPresenter()
    val views = createScopeViews(app)

    // Create root ViewController
    val viewController = UIViewController()
    viewController.view.backgroundColor = StoreColors.Background

    // Start navigation
    kotlinx.coroutines.runBlocking { RootKeys(app).flip() }

    // Mount root view
    val rootView = views.viewOf(app.scope) as? AbstractViewIos<*>

    if (rootView != null) {
        val rv = rootView.rootView
        rv.translatesAutoresizingMaskIntoConstraints = false
        viewController.view.addSubview(rv)

        NSLayoutConstraint.activateConstraints(listOf(
            rv.topAnchor.constraintEqualToAnchor(viewController.view.safeAreaLayoutGuide.topAnchor),
            rv.leadingAnchor.constraintEqualToAnchor(viewController.view.leadingAnchor),
            rv.trailingAnchor.constraintEqualToAnchor(viewController.view.trailingAnchor),
            rv.bottomAnchor.constraintEqualToAnchor(viewController.view.bottomAnchor)
        ))
    }

    return viewController
}

/**
 * Ensures the given block runs on the main thread.
 * If already on main, executes directly; otherwise dispatches synchronously.
 */
private fun <T> onMainThread(block: () -> T): T {
    return if (NSThread.isMainThread) {
        block()
    } else {
        runBlocking(Dispatchers.Main) { block() }
    }
}

/**
 * The iOS views: which view draws each kind of scope, and the view of each scope on screen — created the first time
 * its scope is asked for ([ScopeViews.viewOf]) and released with it. Views are UIKit objects, so they are built on the
 * main thread.
 */
private fun createScopeViews(app: RootPresenter): ScopeViews {
    val views = ScopeViews()
    return views
        .register<RootScope> { onMainThread { RootViewIos(app, views, it).initialize() } }
        .register<LoginScope> { onMainThread { LoginViewIos(app, it).initialize() } }
        .register<HomeScope> { onMainThread { HomeViewIos(app, views, it).initialize() } }
        .register<ProductsPanelScope> { onMainThread { ProductsPanelViewIos(app, it).initialize() } }
        .register<PurchasesPanelScope> { onMainThread { PurchasesPanelViewIos(app, it).initialize() } }
        .register<ProductScope> { onMainThread { ProductViewIos(app, it).initialize() } }
        .register<CartScope> { onMainThread { CartViewIos(app, views, it).initialize() } }
        .register<CartItemScope> { onMainThread { CartItemViewIos(app, it).initialize() } }
        .register<ReceiptScope> { onMainThread { ReceiptViewIos(app, it).initialize() } }
}
