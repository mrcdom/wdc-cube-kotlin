package br.com.wdc.shopping.nativeui.ios

import br.com.wdc.framework.commons.concurrent.IosScheduledExecutor
import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.shopping.domain.security.IosCryptoProvider
import br.com.wdc.shopping.nativeui.ios.theme.ShoppingColors
import br.com.wdc.shopping.nativeui.ios.toolkit.AbstractViewIos
import br.com.wdc.shopping.nativeui.ios.toolkit.ViewUtils
import br.com.wdc.shopping.nativeui.ios.views.*
import br.com.wdc.shopping.persistence.client.IosHttpTransport
import br.com.wdc.shopping.persistence.client.RestConfig
import br.com.wdc.shopping.persistence.client.RestRepositoryBootstrap
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.open.login.LoginPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptPresenter
import kotlinx.cinterop.ExperimentalForeignApi
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

    // Register view factories
    registerViewFactories()

    // Create the application
    val app = IosNativeShoppingApplication()

    // Create root ViewController
    val viewController = UIViewController()
    viewController.view.backgroundColor = ShoppingColors.Background

    // Start navigation
    app.go(app.getRootPlace().placeName)

    // Mount root view
    val rootPresenter = app.getRootPresenter()
    val rootView = rootPresenter?.view() as? AbstractViewIos<*>

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
 * Registers CubeView factory lambdas for all presenters.
 */
private fun registerViewFactories() {
    RootPresenter.createView = { presenter ->
        RootViewIos(presenter).initialize()
    }

    LoginPresenter.createView = { presenter ->
        LoginViewIos(presenter).initialize()
    }

    HomePresenter.createView = { presenter ->
        HomeViewIos(presenter).initialize()
    }

    ProductsPanelPresenter.createView = { presenter ->
        ProductsPanelViewIos(presenter).initialize()
    }

    PurchasesPanelPresenter.createView = { presenter ->
        PurchasesPanelViewIos(presenter).initialize()
    }

    ProductPresenter.createView = { presenter ->
        ProductViewIos(presenter).initialize()
    }

    CartPresenter.createView = { presenter ->
        CartViewIos(presenter).initialize()
    }

    ReceiptPresenter.createView = { presenter ->
        ReceiptViewIos(presenter).initialize()
    }
}
