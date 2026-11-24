package br.com.wdc.shopping.nativeui.ios

import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.framework.commons.concurrent.IosScheduledExecutor
import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.cube.ScopeViews
import br.com.wdc.shopping.domain.security.IosCryptoProvider
import br.com.wdc.shopping.nativeui.ios.theme.ShoppingColors
import br.com.wdc.shopping.nativeui.ios.toolkit.AbstractViewIos
import br.com.wdc.shopping.nativeui.ios.toolkit.ViewUtils
import br.com.wdc.shopping.nativeui.ios.views.*
import br.com.wdc.shopping.persistence.client.IosHttpTransport
import br.com.wdc.shopping.persistence.client.RestConfig
import br.com.wdc.shopping.persistence.client.RestRepositoryBootstrap
import br.com.wdc.shopping.presentation.presenter.RootKeys
import br.com.wdc.shopping.presentation.presenter.RootScope
import br.com.wdc.shopping.presentation.presenter.open.login.LoginScope
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartItemScope
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.ProductsPanelScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.PurchasesPanelScope
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductScope
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptScope
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
 * Creates the main UIViewController for the iOS native Shopping app.
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
    app.scopeViews = createScopeViews(app)

    // Create root ViewController
    val viewController = UIViewController()
    viewController.view.backgroundColor = ShoppingColors.Background

    // Start navigation
    kotlinx.coroutines.runBlocking { RootKeys(app).flip() }

    // Mount root view
    val rootView = app.view as? AbstractViewIos<*>

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
 * The view catalog: which iOS view draws each kind of scope. The application creates a view the first
 * time its scope is asked for ([RootPresenter.viewOf]); views are UIKit objects, so they are built on the main thread.
 */
private fun createScopeViews(app: RootPresenter): ScopeViews = ScopeViews()
    .register<RootScope> { onMainThread { RootViewIos(app, it).initialize() } }
    .register<LoginScope> { onMainThread { LoginViewIos(app, it).initialize() } }
    .register<HomeScope> { onMainThread { HomeViewIos(app, it).initialize() } }
    .register<ProductsPanelScope> { onMainThread { ProductsPanelViewIos(app, it).initialize() } }
    .register<PurchasesPanelScope> { onMainThread { PurchasesPanelViewIos(app, it).initialize() } }
    .register<ProductScope> { onMainThread { ProductViewIos(app, it).initialize() } }
    .register<CartScope> { onMainThread { CartViewIos(app, it).initialize() } }
    .register<CartItemScope> { onMainThread { CartItemViewIos(app, it).initialize() } }
    .register<ReceiptScope> { onMainThread { ReceiptViewIos(app, it).initialize() } }
