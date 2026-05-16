package br.com.wdc.shopping.view.compose.web

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import br.com.wdc.framework.commons.codec.Base62
import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.concurrent.WasmScheduledExecutor
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.framework.commons.storage.WasmSessionStorage
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubePresenter
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.security.CryptoProvider
import br.com.wdc.shopping.domain.security.WasmCryptoProvider
import br.com.wdc.shopping.persistence.client.RestConfig
import br.com.wdc.shopping.persistence.client.RestRepositoryBootstrap
import br.com.wdc.shopping.persistence.client.WasmHttpTransport
import br.com.wdc.shopping.presentation.repository.SecuredProductRepository
import br.com.wdc.shopping.presentation.repository.SecuredPurchaseItemRepository
import br.com.wdc.shopping.presentation.repository.SecuredPurchaseRepository
import br.com.wdc.shopping.presentation.repository.SecuredUserRepository
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.Routes
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.open.login.LoginPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptPresenter
import br.com.wdc.shopping.view.compose.bridge.ComposeCubeView
import br.com.wdc.shopping.view.compose.views.*
import br.com.wdc.shopping.view.compose.theme.ShoppingTheme
import kotlinx.browser.document

/**
 * Fixed HMAC key used for signing intents when no user is authenticated.
 * After login, the per-session secret from the server is used instead.
 */
private const val ANONYMOUS_SIGN_KEY = "wdc-shopping-anonymous-intent-key"

/** REST configuration holding the auth client with per-session intent signing secret. */
private lateinit var platformConfig: RestConfig

private class ComposeShoppingApplication : ShoppingApplication() {

    private val attributes = mutableMapOf<String, Any?>()

    override fun setAttribute(name: String, value: Any?): Any? = attributes.put(name, value)

    override fun getAttribute(name: String): Any? = attributes[name]

    override fun removeAttribute(name: String): Any? = attributes.remove(name)

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
        jsSetLocationHash(newFragment.toJsString())
    }

    fun safeGo(path: String?) {
        val intent = CubeIntent.parse(path ?: "")
        if (intent.place == null) {
            intent.place = getRootPlace()
        }

        val actualSignature = (intent.removeParameter("sign") as? String) ?: ""
        val intentStr = intent.toString()

        if (verifyIntent(intentStr, actualSignature)) {
            go(intent)
        } else {
            // Invalid signature: navigate to current state or root
            updateHistory()
            val newIntent = newIntent()
            if (newIntent.place == null) {
                newIntent.place = getRootPlace()
            }
            go(newIntent)
        }
    }

    override fun createPresenterMap(): MutableMap<Int, CubePresenter> = LinkedHashMap()

    override fun createUserDelegate(delegate: UserRepository) =
        SecuredUserRepository(delegate) { getSecurityContext() }

    override fun createProductDelegate(delegate: ProductRepository) =
        SecuredProductRepository(delegate) { getSecurityContext() }

    override fun createPurchaseDelegate(delegate: PurchaseRepository) =
        SecuredPurchaseRepository(delegate) { getSecurityContext() }

    override fun createPurchaseItemDelegate(delegate: PurchaseItemRepository) =
        SecuredPurchaseItemRepository(delegate) { getSecurityContext() }

    override fun createSessionStorage(): SessionStorage = WasmSessionStorage()
}

private fun createView(view: ComposeCubeView): CubeView {
    return view
}

private fun initializePlatform(baseUrl: String) {
    // Install platform services
    JsonInputFactory.installCommon()
    JsonOutputFactory.installCommon()
    ScheduledExecutor.BEAN.set(WasmScheduledExecutor())

    // Initialize REST repositories
    val transport = WasmHttpTransport(baseUrl)
    val config = RestConfig(transport)
    platformConfig = config
    RestRepositoryBootstrap.initialize(config, WasmCryptoProvider())

    // Wire view factories
    RootPresenter.createView = { p -> createView(RootView(p)) }
    LoginPresenter.createView = { p -> createView(LoginView(p)) }
    HomePresenter.createView = { p -> createView(HomeView(p)) }
    CartPresenter.createView = { p -> createView(CartView(p)) }
    ProductPresenter.createView = { p -> createView(ProductView(p)) }
    ReceiptPresenter.createView = { p -> createView(ReceiptView(p)) }
    ProductsPanelPresenter.createView = { p -> createView(ProductsPanelView(p)) }
    PurchasesPanelPresenter.createView = { p -> createView(PurchasesPanelView(p)) }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val baseUrl = getBaseUrl()
    initializePlatform(baseUrl)

    val app = ComposeShoppingApplication()

    // Restore auth state (including intentSignSecret) BEFORE first navigation,
    // so that safeGo() can verify URL signatures made with the session key.
    restoreAuthState(app)

    // Ensure route registrations are initialized before navigation
    Routes.Place.entries

    // Read initial path from URL hash, verify signature, or default to "public"
    val hash = getLocationHash()
    if (hash.isNotBlank()) {
        app.safeGo(hash)
    } else {
        app.go("public")
    }

    // Listen for browser back/forward and manual URL hash changes
    jsOnHashChange {
        val newHash = getLocationHash()
        if (newHash != app.fragment) {
            if (newHash.isNotBlank()) {
                app.safeGo(newHash)
            } else {
                app.go("public")
            }
        }
    }

    val target = document.getElementById("ComposeTarget") ?: return

    ComposeViewport(target) {
        ShoppingTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
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

@JsFun("() => window.location.origin")
private external fun jsLocationOrigin(): JsString

/**
 * Eagerly restores auth state from session storage so that the per-session
 * intentSignSecret is available before the first [safeGo] call.
 */
private fun restoreAuthState(app: ComposeShoppingApplication) {
    val authService = AuthenticationService.BEAN.getOrNull() ?: return
    val json = app.sessionStorage.getString("authState") ?: return
    try {
        val inp = JsonInputFactory.createStringInput(json).input
        authService.readAuthState(inp)
    } catch (_: Exception) {
        app.sessionStorage.remove("authState")
    }
}

private fun getBaseUrl(): String {
    return jsLocationOrigin().toString()
}

@JsFun("() => { const h = window.location.hash; return h.startsWith('#') ? h.substring(1) : h; }")
private external fun jsGetLocationHash(): JsString

private fun getLocationHash(): String {
    return jsGetLocationHash().toString()
}

@JsFun("(hash) => { window.location.hash = '#' + hash; }")
private external fun jsSetLocationHash(hash: JsString)

@JsFun("(callback) => { window.addEventListener('hashchange', () => callback()); }")
private external fun jsOnHashChange(callback: () -> Unit)

// --- Local HMAC intent signing ---

/**
 * Returns the HMAC signing key: per-session secret if authenticated,
 * or the fixed anonymous key otherwise.
 */
private fun getSigningKey(): String {
    return platformConfig.authClient?.intentSignSecret ?: ANONYMOUS_SIGN_KEY
}

/**
 * Signs an intent string using HMAC-SHA256 with the current signing key.
 * Returns the signature encoded as Base62 (URL-safe).
 */
private fun signIntent(intentStr: String): String {
    val crypto = CryptoProvider.BEAN.get()
    val key = getSigningKey()
    val hash = crypto.hmacSha256(key.encodeToByteArray(), intentStr.encodeToByteArray())
    return Base62.encodeToString(hash)
}

/**
 * Verifies that the given signature matches the expected HMAC for [intentStr].
 * Tries both the current signing key and the anonymous key (in case the user
 * just logged in and the URL was signed with the anonymous key, or vice versa).
 */
private fun verifyIntent(intentStr: String, signature: String): Boolean {
    if (signature.isBlank()) return false
    val crypto = CryptoProvider.BEAN.get()

    // Try current key (per-session or anonymous)
    val currentKey = getSigningKey()
    val currentHash = crypto.hmacSha256(currentKey.encodeToByteArray(), intentStr.encodeToByteArray())
    if (Base62.encodeToString(currentHash) == signature) return true

    // Try anonymous key as fallback (transition from anonymous to authenticated)
    if (currentKey != ANONYMOUS_SIGN_KEY) {
        val anonHash = crypto.hmacSha256(ANONYMOUS_SIGN_KEY.encodeToByteArray(), intentStr.encodeToByteArray())
        if (Base62.encodeToString(anonHash) == signature) return true
    }

    return false
}
