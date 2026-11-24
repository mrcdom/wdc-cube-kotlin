package br.com.wdc.shopping.nativeui.android

import br.com.wdc.shopping.presentation.presenter.RootPresenter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
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
import br.com.wdc.framework.cube.ScopeViews
import br.com.wdc.shopping.domain.security.JceCryptoProvider
import br.com.wdc.shopping.nativeui.android.toolkit.AbstractViewAndroid
import br.com.wdc.shopping.nativeui.android.toolkit.ViewUpdateScheduler
import br.com.wdc.shopping.nativeui.android.toolkit.ViewUtils
import br.com.wdc.shopping.nativeui.android.views.*
import br.com.wdc.shopping.persistence.client.OkHttpTransport
import br.com.wdc.shopping.persistence.client.RestConfig
import br.com.wdc.shopping.persistence.client.RestRepositoryBootstrap
import br.com.wdc.shopping.presentation.presenter.RootKeys
import br.com.wdc.shopping.presentation.presenter.RootScope
import br.com.wdc.shopping.presentation.presenter.open.login.LoginScope
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelScope
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductScope
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow rotation on tablets (manifest locks portrait by default for phones)
        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        if (isTablet) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

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

        val prefs = getSharedPreferences("wdc_session", MODE_PRIVATE)
        val app = AndroidNativeRootPresenter(AndroidPersistentSessionStorage(prefs))
        registerViews(app)
        ViewUpdateScheduler.initialize { app }
        runBlocking { RootKeys(app).flip() }

        val rootView = (app.view as? AbstractViewAndroid<*>)?.rootView

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

    /**
     * The catalog of views, set before the first navigation. Android views must be built on the main
     * thread; the application creates each view once per scope ([RootPresenter.viewOf]).
     */
    private fun registerViews(app: RootPresenter) {
        app.scopeViews = ScopeViews()
            .register<RootScope> { onMainThread { RootViewAndroid(app, it).initialize() } }
            .register<LoginScope> { onMainThread { LoginViewAndroid(app, it).initialize() } }
            .register<HomeScope> { onMainThread { HomeViewAndroid(app, it).initialize() } }
            .register<ProductsPanelScope> { onMainThread { ProductsPanelViewAndroid(app, it).initialize() } }
            .register<PurchasesPanelScope> { onMainThread { PurchasesPanelViewAndroid(app, it).initialize() } }
            .register<ProductScope> { onMainThread { ProductViewAndroid(app, it).initialize() } }
            .register<CartScope> { onMainThread { CartViewAndroid(app, it).initialize() } }
            .register<ReceiptScope> { onMainThread { ReceiptViewAndroid(app, it).initialize() } }
    }

    private fun <T> onMainThread(block: () -> T): T {
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            runBlocking(Dispatchers.Main) { block() }
        }
    }
}
