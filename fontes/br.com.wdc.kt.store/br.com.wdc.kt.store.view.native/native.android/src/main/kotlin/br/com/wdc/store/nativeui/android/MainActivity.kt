package br.com.wdc.store.nativeui.android

import br.com.wdc.store.presentation.presenter.RootPresenter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import android.os.StrictMode
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import br.com.wdc.store.nativeui.android.theme.StoreColors
import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.commons.storage.AndroidPersistentSessionStorage
import br.com.wdc.framework.cube.ScopeViews
import br.com.wdc.store.domain.security.JceCryptoProvider
import br.com.wdc.store.nativeui.android.toolkit.AbstractViewAndroid
import br.com.wdc.store.nativeui.android.toolkit.ViewUpdateScheduler
import br.com.wdc.store.nativeui.android.toolkit.ViewUtils
import br.com.wdc.store.nativeui.android.views.*
import br.com.wdc.store.persistence.client.OkHttpTransport
import br.com.wdc.store.persistence.client.RestConfig
import br.com.wdc.store.persistence.client.RestRepositoryBootstrap
import br.com.wdc.store.presentation.presenter.RootKeys
import br.com.wdc.store.presentation.presenter.RootScope
import br.com.wdc.store.presentation.presenter.open.login.LoginScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartItemScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.store.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.store.presentation.presenter.restricted.home.ProductsPanelScope
import br.com.wdc.store.presentation.presenter.restricted.home.PurchasesPanelScope
import br.com.wdc.store.presentation.presenter.restricted.products.ProductScope
import br.com.wdc.store.presentation.presenter.restricted.receipt.ReceiptScope
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
        window.statusBarColor = StoreColors.Primary

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
        val views = createViews(app)
        ViewUpdateScheduler.initialize { app }
        runBlocking { RootKeys(app).flip() }

        val rootView = (views.viewOf(app.scope) as? AbstractViewAndroid<*>)?.rootView

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
     * The Android views: which view draws each kind of scope, and the view of each scope on screen — created the
     * first time its scope is asked for ([ScopeViews.viewOf]) and released with it. Android views must be built on
     * the main thread.
     */
    private fun createViews(app: RootPresenter): ScopeViews {
        val views = ScopeViews()
        return views
            .register<RootScope> { onMainThread { RootViewAndroid(app, views, it).initialize() } }
            .register<LoginScope> { onMainThread { LoginViewAndroid(app, it).initialize() } }
            .register<HomeScope> { onMainThread { HomeViewAndroid(app, views, it).initialize() } }
            .register<ProductsPanelScope> { onMainThread { ProductsPanelViewAndroid(app, it).initialize() } }
            .register<PurchasesPanelScope> { onMainThread { PurchasesPanelViewAndroid(app, it).initialize() } }
            .register<ProductScope> { onMainThread { ProductViewAndroid(app, it).initialize() } }
            .register<CartScope> { onMainThread { CartViewAndroid(app, views, it).initialize() } }
            .register<CartItemScope> { onMainThread { CartItemViewAndroid(app, it).initialize() } }
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
