package br.com.wdc.store.nativeui.android

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.StrictMode
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import br.com.wdc.store.nativeui.android.theme.StoreColors
import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.commons.storage.AndroidPersistentSessionStorage
import br.com.wdc.store.domain.security.JceCryptoProvider
import br.com.wdc.store.nativeui.android.toolkit.AbstractViewAndroid
import br.com.wdc.store.nativeui.android.toolkit.ViewUpdateScheduler
import br.com.wdc.store.nativeui.android.toolkit.ViewUtils
import br.com.wdc.store.nativeui.android.views.RootViewAndroid
import br.com.wdc.store.persistence.client.OkHttpTransport
import br.com.wdc.store.persistence.client.RestConfig
import br.com.wdc.store.persistence.client.RestRepositoryBootstrap
import br.com.wdc.store.presentation.presenter.RootKeys
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    /** The root view: releasing it takes every view it holds along. */
    private var root: AbstractViewAndroid<*>? = null

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
        ViewUpdateScheduler.initialize { app }
        runBlocking { RootKeys(app).flip() }

        // every other view is asked for by the view that holds its scope
        val root = app.viewOf(app.scope).also { this.root = it }
        setContentView(root.rootView)
    }

    override fun onDestroy() {
        root?.release()
        root = null
        super.onDestroy()
    }

    private fun initializePlatform(baseUrl: String) {
        JsonInputFactory.installCommon()
        JsonOutputFactory.installCommon()
        ScheduledExecutor.BEAN.set(AndroidScheduledExecutor())

        val transport = OkHttpTransport(baseUrl)
        val config = RestConfig(transport)
        RestRepositoryBootstrap.initialize(config, JceCryptoProvider())
    }
}
