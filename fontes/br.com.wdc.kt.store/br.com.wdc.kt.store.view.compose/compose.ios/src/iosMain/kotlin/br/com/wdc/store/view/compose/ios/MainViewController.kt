package br.com.wdc.store.view.compose.ios

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import br.com.wdc.framework.commons.concurrent.IosScheduledExecutor
import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.commons.storage.IosPersistentSessionStorage
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.store.domain.product.ProductRepository
import br.com.wdc.store.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.store.domain.purchase.PurchaseRepository
import br.com.wdc.store.domain.user.UserRepository
import br.com.wdc.store.domain.security.IosCryptoProvider
import br.com.wdc.store.persistence.client.IosHttpTransport
import br.com.wdc.store.persistence.client.RestConfig
import br.com.wdc.store.persistence.client.RestRepositoryBootstrap
import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.RootKeys
import br.com.wdc.store.view.compose.bridge.ViewUpdateScheduler
import br.com.wdc.store.view.compose.theme.StoreTheme
import br.com.wdc.store.view.compose.util.PlatformConfig
import br.com.wdc.store.view.compose.views.*
import platform.UIKit.UIViewController
import kotlinx.coroutines.launch

private class IosRootPresenter : RootPresenter() {

    override fun createSessionStorage(): SessionStorage = IosPersistentSessionStorage()
}

private fun initializePlatform(baseUrl: String) {
    // Configure platform base URL for image loading
    PlatformConfig.baseUrl = baseUrl

    // Install platform services
    JsonInputFactory.installCommon()
    JsonOutputFactory.installCommon()
    ScheduledExecutor.BEAN.set(IosScheduledExecutor())

    // Initialize REST repositories
    val transport = IosHttpTransport(baseUrl)
    val config = RestConfig(transport)
    RestRepositoryBootstrap.initialize(config, IosCryptoProvider())
}

/**
 * Creates the main UIViewController for the Store app.
 * Call from Swift: MainViewControllerKt.MainViewController(baseUrl: "http://...")
 */
fun MainViewController(baseUrl: String): UIViewController {
    initializePlatform(baseUrl)

    val app = IosRootPresenter()
    ViewUpdateScheduler.initialize { app }
    // the first navigation runs in the presenters' scope: the network is waited for without blocking this thread,
    // and the window, shown right away, repaints when it arrives
    app.presenterScope.launch { RootKeys(app).flip() }

    return ComposeUIViewController {
        StoreTheme {
            Surface(
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                color = MaterialTheme.colorScheme.background
            ) {
                StoreApp(app)
            }
        }
    }
}
