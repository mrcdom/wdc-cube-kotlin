package br.com.wdc.shopping.nativeui.web

import br.com.wdc.framework.commons.codec.Base62
import br.com.wdc.framework.commons.concurrent.JsScheduledExecutor
import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.commons.storage.JsSessionStorage
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubePresenter
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.security.CryptoProvider
import br.com.wdc.shopping.domain.security.JsCryptoProvider
import br.com.wdc.shopping.nativeui.web.bridge.ReactCubeView
import br.com.wdc.shopping.nativeui.web.theme.ShoppingTheme
import br.com.wdc.shopping.nativeui.web.views.*
import br.com.wdc.shopping.persistence.client.JsHttpTransport
import br.com.wdc.shopping.persistence.client.RestConfig
import br.com.wdc.shopping.persistence.client.RestRepositoryBootstrap
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
import br.com.wdc.shopping.presentation.repository.SecuredProductRepository
import br.com.wdc.shopping.presentation.repository.SecuredPurchaseItemRepository
import br.com.wdc.shopping.presentation.repository.SecuredPurchaseRepository
import br.com.wdc.shopping.presentation.repository.SecuredUserRepository
import kotlinx.browser.window
import mui.material.CssBaseline
import mui.material.styles.ThemeProvider
import react.FC
import react.Props
import react.create
import react.dom.client.createRoot
import react.useEffect
import react.useState
import web.dom.ElementId
import web.dom.document

/**
 * Fixed HMAC key used for signing intents when no user is authenticated.
 * After login, the per-session secret from the server is used instead.
 */
private const val ANONYMOUS_SIGN_KEY = "wdc-shopping-anonymous-intent-key"

/** REST configuration holding the auth client with per-session intent signing secret. */
private lateinit var platformConfig: RestConfig

private class JsShoppingApplication : ShoppingApplication() {

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
        window.location.hash = "#$newFragment"
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

    override fun createSessionStorage(): SessionStorage = JsSessionStorage()
}

private fun createView(view: ReactCubeView): CubeView = view

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

    val app = JsShoppingApplication()

    // On irrecoverable auth failure (401 + refresh failed), clear session and go to login
    platformConfig.transport.onAuthFailure = {
        platformConfig.authClient?.clearTokens()
        app.sessionStorage.remove("authState")
        app.sessionStorage.remove("securityContext")
        app.setSecurityContext(null)
        app.go("public")
    }

    // Restore auth state BEFORE first navigation
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
    window.addEventListener("hashchange", {
        val newHash = getLocationHash()
        if (newHash != app.fragment) {
            if (newHash.isNotBlank()) {
                app.safeGo(newHash)
            } else {
                app.go("public")
            }
        }
    })

    val container = document.getElementById(ElementId("app")) ?: return

    val rootComponent = FC<Props> {
        ThemeProvider {
            theme = ShoppingTheme

            CssBaseline()

            // Subscribe to root presenter updates
            var rev by useState(0)

            val rootPresenter = app.getRootPresenter()
            val rootView = rootPresenter?.view() as? RootView

            useEffect(rootView) {
                if (rootView != null) {
                    rootView.onUpdate = { rev++ }
                }
            }

            @Suppress("UNUSED_VARIABLE")
            val unused = rev

            rootView?.component {}
        }
    }

    createRoot(container).render(rootComponent.create())
}

/**
 * Eagerly restores auth state from session storage so that the per-session
 * intentSignSecret is available before the first [safeGo] call.
 */
private fun restoreAuthState(app: JsShoppingApplication) {
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
