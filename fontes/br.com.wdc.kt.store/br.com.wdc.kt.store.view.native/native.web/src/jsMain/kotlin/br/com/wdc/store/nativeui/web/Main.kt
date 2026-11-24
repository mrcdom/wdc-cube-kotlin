package br.com.wdc.store.nativeui.web

import br.com.wdc.framework.commons.codec.Base62
import br.com.wdc.framework.commons.concurrent.JsScheduledExecutor
import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.commons.storage.JsSessionStorage
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.ScopeViews
import br.com.wdc.store.domain.product.ProductRepository
import br.com.wdc.store.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.store.domain.purchase.PurchaseRepository
import br.com.wdc.store.domain.user.UserRepository
import br.com.wdc.store.domain.security.AuthenticationService
import br.com.wdc.store.domain.security.CryptoProvider
import br.com.wdc.store.domain.security.JsCryptoProvider
import br.com.wdc.store.nativeui.web.bridge.ViewUpdateScheduler
import kotlinx.coroutines.launch
import br.com.wdc.store.nativeui.web.theme.StoreTheme
import br.com.wdc.store.nativeui.web.views.*
import br.com.wdc.store.persistence.client.JsHttpTransport
import br.com.wdc.store.persistence.client.RestConfig
import br.com.wdc.store.persistence.client.RestRepositoryBootstrap
import br.com.wdc.store.presentation.presenter.RootPresenter
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
import kotlinx.browser.window
import mui.material.CssBaseline
import mui.material.styles.ThemeProvider
import react.FC
import react.Props
import react.create
import react.dom.client.createRoot
import web.dom.ElementId
import web.dom.document

/**
 * Fixed HMAC key used for signing intents when no user is authenticated.
 * After login, the per-session secret from the server is used instead.
 */
private const val ANONYMOUS_SIGN_KEY = "wdc-store-anonymous-intent-key"

/** REST configuration holding the auth client with per-session intent signing secret. */
private lateinit var platformConfig: RestConfig

private class JsRootPresenter : RootPresenter() {

    override fun updateHistory() {
        val intent = CubeIntent()
        intent.place = getLastPlace() ?: getRootPlace()
        publishParameters(intent)

        val intentStr = intent.toString()
        val signature = signIntent(intentStr)
        intent.setParameter("sign", signature)

        val newFragment = intent.toString()
        fragment = newFragment
        if (newFragment == getLocationHash()) return
        window.location.hash = "#$newFragment"
    }

    suspend fun safeGo(path: String?) {
        val intent = CubeIntent.parse(path ?: "")
        if (intent.place == null) {
            intent.place = getRootPlace()
        }

        val actualSignature = (intent.removeParameter("sign") as? String) ?: ""
        val intentStr = intent.toString()

        if (verifyIntent(intentStr, actualSignature)) {
            flip(intent)
        } else {
            updateHistory()
            val newIntent = newIntent()
            if (newIntent.place == null) {
                newIntent.place = getRootPlace()
            }
            flip(newIntent)
        }
    }

    override fun createSessionStorage(): SessionStorage = JsSessionStorage()
}

private fun initializePlatform(baseUrl: String) {
    // Install platform services
    JsonInputFactory.installCommon()
    JsonOutputFactory.installCommon()
    ScheduledExecutor.BEAN.set(JsScheduledExecutor())

    // Initialize REST repositories
    val transport = JsHttpTransport(baseUrl)
    val config = RestConfig(transport)
    platformConfig = config
    RestRepositoryBootstrap.initialize(config, JsCryptoProvider())
}

fun main() {
    // Inject subtle scrollbar CSS: thumb is white (invisible) by default, visible on hover
    document.head?.let { head ->
        val style = document.createElement("style")
        style.textContent = """
            ::-webkit-scrollbar { width: 6px; }
            ::-webkit-scrollbar-track { background: transparent; }
            ::-webkit-scrollbar-thumb { background-color: white; border-radius: 3px; transition: background-color 0.2s; }
            ::-webkit-scrollbar-thumb:hover { background-color: rgba(0,0,0,0.25); }
            ::-webkit-scrollbar-thumb:active { background-color: rgba(0,0,0,0.4); }
            * { scrollbar-width: thin; scrollbar-color: white transparent; }
            *:hover { scrollbar-color: rgba(0,0,0,0.15) transparent; }
        """.trimIndent()
        head.appendChild(style)
    }

    val baseUrl = window.location.origin
    initializePlatform(baseUrl)

    val app = JsRootPresenter()
    // The view of each scope, created by the application on first use — before the first navigation
    app.scopeViews = ScopeViews()
        .register<RootScope> { RootView(app, it) }
        .register<LoginScope> { LoginView(app, it) }
        .register<HomeScope> { HomeView(app, it) }
        .register<CartScope> { CartView(app, it) }
        .register<CartItemScope> { CartItemView(app, it) }
        .register<ProductScope> { ProductView(app, it) }
        .register<ReceiptScope> { ReceiptView(app, it) }
        .register<ProductsPanelScope> { ProductsPanelView(app, it) }
        .register<PurchasesPanelScope> { PurchasesPanelView(app, it) }
    ViewUpdateScheduler.initialize { app }

    // On irrecoverable auth failure (401 + refresh failed), clear session and go to login
    platformConfig.transport.onAuthFailure = {
        app.presenterScope.launch {
            platformConfig.authClient?.clearTokens()
            app.sessionStorage.remove("authState")
            RootKeys(app).flip()
        }
    }

    // Restore auth state BEFORE first navigation
    restoreAuthState(app)

    // Read initial path from URL hash, verify signature, or default to "public"
    app.presenterScope.launch {
        val hash = getLocationHash()
        if (hash.isNotBlank()) {
            app.safeGo(hash)
        } else {
            RootKeys(app).flip()
        }
    }

    // Listen for browser back/forward and manual URL hash changes
    window.addEventListener("hashchange", {
        app.presenterScope.launch {
            val newHash = getLocationHash()
            if (newHash != app.fragment) {
                if (newHash.isNotBlank()) {
                    app.safeGo(newHash)
                } else {
                    RootKeys(app).flip()
                }
            }
        }
    })

    val container = document.getElementById(ElementId("app")) ?: return

    val rootComponent = FC<Props> {
        ThemeProvider {
            theme = StoreTheme

            CssBaseline()

            val rootView = app.view as? RootView

            rootView?.component {}
        }
    }

    createRoot(container).render(rootComponent.create())
}

/**
 * Eagerly restores auth state from session storage so that the per-session
 * intentSignSecret is available before the first [safeGo] call.
 */
private fun restoreAuthState(app: JsRootPresenter) {
    val authService = AuthenticationService.BEAN.getOrNull() ?: return
    val json = app.sessionStorage.getString("authState") ?: return
    try {
        val inp = JsonInputFactory.createStringInput(json).input
        authService.readAuthState(inp)
    } catch (_: Exception) {
        app.sessionStorage.remove("authState")
    }
}

private fun getLocationHash(): String {
    val h = window.location.hash
    return if (h.startsWith("#")) h.substring(1) else h
}

// --- Local HMAC intent signing ---

private fun getSigningKey(): String {
    return platformConfig.authClient?.intentSignSecret ?: ANONYMOUS_SIGN_KEY
}

private fun signIntent(intentStr: String): String {
    val crypto = CryptoProvider.BEAN.get()
    val key = getSigningKey()
    val hash = crypto.hmacSha256(key.encodeToByteArray(), intentStr.encodeToByteArray())
    return Base62.encodeToString(hash)
}

private fun verifyIntent(intentStr: String, signature: String): Boolean {
    if (signature.isBlank()) return false
    val crypto = CryptoProvider.BEAN.get()

    val currentKey = getSigningKey()
    val currentHash = crypto.hmacSha256(currentKey.encodeToByteArray(), intentStr.encodeToByteArray())
    if (Base62.encodeToString(currentHash) == signature) return true

    if (currentKey != ANONYMOUS_SIGN_KEY) {
        val anonHash = crypto.hmacSha256(ANONYMOUS_SIGN_KEY.encodeToByteArray(), intentStr.encodeToByteArray())
        if (Base62.encodeToString(anonHash) == signature) return true
    }

    return false
}
