package br.com.wdc.store.nativeui.ios

import br.com.wdc.framework.commons.concurrent.IosScheduledExecutor
import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.store.domain.security.IosCryptoProvider
import br.com.wdc.store.nativeui.ios.theme.StoreColors
import br.com.wdc.store.nativeui.ios.toolkit.ViewUtils
import br.com.wdc.store.persistence.client.IosHttpTransport
import br.com.wdc.store.persistence.client.RestConfig
import br.com.wdc.store.persistence.client.RestRepositoryBootstrap
import br.com.wdc.store.presentation.presenter.RootKeys
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.*
import kotlinx.coroutines.launch

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

    // Create the application (it holds the view catalog)
    val app = IosNativeRootPresenter()

    // Create root ViewController
    val viewController = UIViewController()
    viewController.view.backgroundColor = StoreColors.Background

    // the first navigation runs in the presenters' scope: the network is waited for without blocking the main thread,
    // and the root view, mounted right away, repaints when it arrives
    app.presenterScope.launch { RootKeys(app).flip() }

    // Mount the root view: every other view is asked for by the view that holds its scope
    run {
        val rv = app.viewOf(app.scope).rootView
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
