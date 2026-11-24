package br.com.wdc.store.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.commons.storage.JvmSessionStorage
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.store.domain.product.ProductRepository
import br.com.wdc.store.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.store.domain.purchase.PurchaseRepository
import br.com.wdc.store.domain.user.UserRepository
import br.com.wdc.store.domain.security.JceCryptoProvider
import br.com.wdc.store.persistence.client.OkHttpTransport
import br.com.wdc.store.persistence.client.RestConfig
import br.com.wdc.store.persistence.client.RestRepositoryBootstrap
import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.RootKeys
import br.com.wdc.store.view.compose.bridge.ViewUpdateScheduler
import br.com.wdc.store.view.compose.theme.StoreTheme
import br.com.wdc.store.view.compose.util.PlatformConfig
import br.com.wdc.store.view.compose.views.*

fun main() {
    // Set macOS app name before any AWT initialization
    System.setProperty("apple.awt.application.name", "WDC Store")

    val baseUrl = System.getProperty("baseUrl", "http://localhost:8080")
    initializePlatform(baseUrl)

    // Set macOS Dock icon
    try {
        val iconUrl = Thread.currentThread().contextClassLoader.getResource("icon.png")
        if (iconUrl != null) {
            val image = javax.imageio.ImageIO.read(iconUrl)
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar.getTaskbar().iconImage = image
            }
        }
    } catch (_: Exception) { }

    val app = DesktopRootPresenter()
    app.scopeViews = composeScopeViews(app)
    ViewUpdateScheduler.initialize { app }
    kotlinx.coroutines.runBlocking { RootKeys(app).flip() }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "WDC Store",
            icon = painterResource("icon.png"),
            state = rememberWindowState(width = 1024.dp, height = 768.dp)
        ) {
            StoreTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    (app.view as? RootView)?.Render()
                }
            }
        }
    }
}

private class DesktopRootPresenter : RootPresenter() {

    override fun createSessionStorage(): SessionStorage = JvmSessionStorage()
}

private fun initializePlatform(baseUrl: String) {
    PlatformConfig.baseUrl = baseUrl
    JsonInputFactory.installCommon()
    JsonOutputFactory.installCommon()
    ScheduledExecutor.BEAN.set(DesktopScheduledExecutor())

    val transport = OkHttpTransport(baseUrl)
    val config = RestConfig(transport)
    RestRepositoryBootstrap.initialize(config, JceCryptoProvider())
}
