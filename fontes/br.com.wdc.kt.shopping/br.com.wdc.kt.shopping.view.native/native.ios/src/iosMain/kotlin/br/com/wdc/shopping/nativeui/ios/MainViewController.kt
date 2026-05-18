package br.com.wdc.shopping.nativeui.ios

import br.com.wdc.framework.commons.concurrent.IosScheduledExecutor
import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.commons.storage.IosPersistentSessionStorage
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.framework.cube.CubePresenter
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.domain.security.IosCryptoProvider
import br.com.wdc.shopping.nativeui.ios.views.*
import br.com.wdc.shopping.persistence.client.IosHttpTransport
import br.com.wdc.shopping.persistence.client.RestConfig
import br.com.wdc.shopping.persistence.client.RestRepositoryBootstrap
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.open.login.LoginPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptPresenter
import br.com.wdc.shopping.presentation.repository.SecuredProductRepository
import br.com.wdc.shopping.presentation.repository.SecuredPurchaseItemRepository
import br.com.wdc.shopping.presentation.repository.SecuredPurchaseRepository
import br.com.wdc.shopping.presentation.repository.SecuredUserRepository
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.*

/**
 * iOS-specific ShoppingApplication implementation.
 */
private class IosNativeShoppingApplication : ShoppingApplication() {

    private val attributes = mutableMapOf<String, Any?>()

    override fun setAttribute(name: String, value: Any?): Any? = attributes.put(name, value)

    override fun getAttribute(name: String): Any? = attributes[name]

    override fun removeAttribute(name: String): Any? = attributes.remove(name)

    override fun updateHistory() {
        // No browser history on iOS
    }

    override fun createPresenterMap(): MutableMap<Int, CubePresenter> = LinkedHashMap()

    override fun createUserDelegate(delegate: UserRepository) =
        SecuredUserRepository(delegate) { getSecurityContext() }

    override fun createProductDelegate(delegate: ProductRepository) =
        SecuredProductRepository(delegate) { getSecurityContext() }

    override fun createPurchaseDelegate(delegate: PurchaseRepository) =
        SecuredPurchaseRepository(delegate) { getSecurityContext() }

    override fun createPurchaseItemDelegate(delegate: PurchaseItemRepository) =
        SecuredPurchaseItemRepository(delegate) { getSecurityContext() }

    override fun createSessionStorage(): SessionStorage = IosPersistentSessionStorage()
}

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
 * Call from Swift: MainViewControllerKt.MainViewController(baseUrl: "http://...")
 */
@OptIn(ExperimentalForeignApi::class)
fun MainViewController(baseUrl: String): UIViewController {
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
