package br.com.wdc.shopping.android

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
import br.com.wdc.framework.cube.CubePresenter
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.domain.security.JceCryptoProvider
import br.com.wdc.shopping.persistence.client.OkHttpTransport
import br.com.wdc.shopping.persistence.client.RestConfig
import br.com.wdc.shopping.persistence.client.RestRepositoryBootstrap
import br.com.wdc.shopping.presentation.repository.SecuredProductRepository
import br.com.wdc.shopping.presentation.repository.SecuredPurchaseItemRepository
import br.com.wdc.shopping.presentation.repository.SecuredPurchaseRepository
import br.com.wdc.shopping.presentation.repository.SecuredUserRepository
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.open.login.LoginPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptPresenter
import br.com.wdc.shopping.view.compose.bridge.ComposeCubeView
import br.com.wdc.shopping.view.compose.bridge.ViewUpdateScheduler
import br.com.wdc.shopping.view.compose.theme.ShoppingTheme
import br.com.wdc.shopping.view.compose.util.PlatformConfig
import br.com.wdc.shopping.view.compose.views.*

class MainActivity : ComponentActivity() {

    private lateinit var app: ShoppingApplication

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
        app = AndroidShoppingApplication(AndroidPersistentSessionStorage(prefs))
        ViewUpdateScheduler.initialize { app }
        app.go("public")

        setContent {
            ShoppingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val rootPresenter = app.getRootPresenter()
                    if (rootPresenter != null) {
                        val rootView = rootPresenter.view() as? RootView
                        rootView?.Render()
                    }
                }
            }
        }
    }
}

private class AndroidShoppingApplication(
    private val persistentStorage: AndroidPersistentSessionStorage
) : ShoppingApplication() {

    private val attributes = mutableMapOf<String, Any?>()

    override fun setAttribute(name: String, value: Any?): Any? = attributes.put(name, value)

    override fun getAttribute(name: String): Any? = attributes[name]

    override fun removeAttribute(name: String): Any? = attributes.remove(name)

    override fun updateHistory() { /* No browser history on Android */ }

    override fun createPresenterMap(): MutableMap<Int, CubePresenter> = LinkedHashMap()

    override fun createUserDelegate(delegate: UserRepository) =
        SecuredUserRepository(delegate) { getSecurityContext() }

    override fun createProductDelegate(delegate: ProductRepository) =
        SecuredProductRepository(delegate) { getSecurityContext() }

    override fun createPurchaseDelegate(delegate: PurchaseRepository) =
        SecuredPurchaseRepository(delegate) { getSecurityContext() }

    override fun createPurchaseItemDelegate(delegate: PurchaseItemRepository) =
        SecuredPurchaseItemRepository(delegate) { getSecurityContext() }

    override fun createSessionStorage(): SessionStorage = persistentStorage
}

private fun createView(view: ComposeCubeView): CubeView = view

private fun initializePlatform(baseUrl: String) {
    PlatformConfig.baseUrl = baseUrl
    JsonInputFactory.installCommon()
    JsonOutputFactory.installCommon()
    ScheduledExecutor.BEAN.set(AndroidScheduledExecutor())

    val transport = OkHttpTransport(baseUrl)
    val config = RestConfig(transport)
    RestRepositoryBootstrap.initialize(config, JceCryptoProvider())

    RootPresenter.createView = { p -> createView(RootView(p)) }
    LoginPresenter.createView = { p -> createView(LoginView(p)) }
    HomePresenter.createView = { p -> createView(HomeView(p)) }
    CartPresenter.createView = { p -> createView(CartView(p)) }
    ProductPresenter.createView = { p -> createView(ProductView(p)) }
    ReceiptPresenter.createView = { p -> createView(ReceiptView(p)) }
    ProductsPanelPresenter.createView = { p -> createView(ProductsPanelView(p)) }
    PurchasesPanelPresenter.createView = { p -> createView(PurchasesPanelView(p)) }
}
