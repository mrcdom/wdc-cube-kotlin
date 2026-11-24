package br.com.wdc.store.android

import android.os.Bundle
import android.os.StrictMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.commons.storage.AndroidPersistentSessionStorage
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

class MainActivity : ComponentActivity() {

    private lateinit var app: RootPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Allow synchronous network calls on the main thread
        // (required by the Cube MVP architecture's synchronous presenter pattern)
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().permitNetwork().build()
        )

        val baseUrl = BuildConfig.BASE_URL
        initializePlatform(baseUrl)

        val prefs = getSharedPreferences("wdc_session", MODE_PRIVATE)
        app = AndroidRootPresenter(AndroidPersistentSessionStorage(prefs))
        ViewUpdateScheduler.initialize { app }
        kotlinx.coroutines.runBlocking { RootKeys(app).flip() }

        setContent {
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
}

private class AndroidRootPresenter(
    private val persistentStorage: AndroidPersistentSessionStorage
) : RootPresenter() {

    override fun createSessionStorage(): SessionStorage = persistentStorage
}

private fun initializePlatform(baseUrl: String) {
    PlatformConfig.baseUrl = baseUrl
    JsonInputFactory.installCommon()
    JsonOutputFactory.installCommon()
    ScheduledExecutor.BEAN.set(AndroidScheduledExecutor())

    val transport = OkHttpTransport(baseUrl)
    val config = RestConfig(transport)
    RestRepositoryBootstrap.initialize(config, JceCryptoProvider())
}
