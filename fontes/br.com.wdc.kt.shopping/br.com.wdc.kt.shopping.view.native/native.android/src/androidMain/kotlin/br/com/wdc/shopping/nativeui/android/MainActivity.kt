package br.com.wdc.shopping.nativeui.android

import android.os.Bundle
import android.os.StrictMode
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import br.com.wdc.shopping.nativeui.android.theme.ShoppingColors
import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.commons.storage.AndroidPersistentSessionStorage
import br.com.wdc.shopping.domain.security.JceCryptoProvider
import br.com.wdc.shopping.nativeui.android.toolkit.AbstractViewAndroid
import br.com.wdc.shopping.nativeui.android.toolkit.ViewUtils
import br.com.wdc.shopping.nativeui.android.views.*
import br.com.wdc.shopping.persistence.client.OkHttpTransport
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

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: app draws behind status bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ShoppingColors.Primary

        // Allow synchronous network on main thread (Cube MVP pattern)
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().permitNetwork().build()
        )

        val baseUrl = BuildConfig.BASE_URL
        initializePlatform(baseUrl)
        ViewUtils.baseUrl = baseUrl
        RootViewAndroid.appContext = this
        registerViewFactories()

        val prefs = getSharedPreferences("wdc_session", MODE_PRIVATE)
        val app = AndroidNativeShoppingApplication(AndroidPersistentSessionStorage(prefs))
        app.go(app.getRootPlace().placeName)

        val rootPresenter = app.getRootPresenter()
        val rootView = (rootPresenter?.view() as? AbstractViewAndroid<*>)?.rootView

        if (rootView != null) {
            setContentView(rootView)
        } else {
            setContentView(FrameLayout(this))
        }
    }

    private fun initializePlatform(baseUrl: String) {
        JsonInputFactory.installCommon()
        JsonOutputFactory.installCommon()
        ScheduledExecutor.BEAN.set(AndroidScheduledExecutor())

        val transport = OkHttpTransport(baseUrl)
        val config = RestConfig(transport)
        RestRepositoryBootstrap.initialize(config, JceCryptoProvider())
    }

    private fun registerViewFactories() {
        RootPresenter.createView = { RootViewAndroid(it).initialize() }
        LoginPresenter.createView = { LoginViewAndroid(it).initialize() }
        HomePresenter.createView = { HomeViewAndroid(it).initialize() }
        ProductsPanelPresenter.createView = { ProductsPanelViewAndroid(it).initialize() }
        PurchasesPanelPresenter.createView = { PurchasesPanelViewAndroid(it).initialize() }
        ProductPresenter.createView = { ProductViewAndroid(it).initialize() }
        CartPresenter.createView = { CartViewAndroid(it).initialize() }
        ReceiptPresenter.createView = { ReceiptViewAndroid(it).initialize() }
    }
}
