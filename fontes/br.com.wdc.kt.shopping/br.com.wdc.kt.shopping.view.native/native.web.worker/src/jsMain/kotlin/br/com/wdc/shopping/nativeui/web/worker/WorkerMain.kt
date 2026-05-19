package br.com.wdc.shopping.nativeui.web.worker

import br.com.wdc.framework.commons.codec.Base62
import br.com.wdc.framework.commons.concurrent.JsScheduledExecutor
import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubePresenter
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.ViewState
import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.security.CryptoProvider
import br.com.wdc.shopping.domain.security.JsCryptoProvider
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

private val LOG = Log.getLogger("Worker")

/** Registry of viewId → presenter for action dispatch */
private val viewRegistry = mutableMapOf<String, Any>()

private var viewCounter = 0

private lateinit var app: WorkerShoppingApplication
private lateinit var platformConfig: RestConfig

private const val ANONYMOUS_SIGN_KEY = "wdc-shopping-anonymous-intent-key"

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

private class WorkerShoppingApplication : ShoppingApplication() {

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

        val msg = js("{}")
        msg.type = "historyUpdate"
        msg.hash = newFragment
        js("self").postMessage(msg)
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

    override fun createSessionStorage(): SessionStorage = WorkerSessionStorage()
}

private fun createWorkerView(viewType: String, presenter: Any, stateProvider: () -> ViewState): CubeView {
    val viewId = "$viewType-${++viewCounter}"
    viewRegistry[viewId] = presenter
    val view = WorkerCubeView(viewId, viewType, stateProvider)

    // Notify main thread of new view
    val msg = js("{}")
    msg.type = "viewCreated"
    msg.viewId = viewId
    msg.viewType = viewType
    js("self").postMessage(msg)

    return view
}

private fun initializePlatform(baseUrl: String) {
    JsonInputFactory.installCommon()
    JsonOutputFactory.installCommon()
    ScheduledExecutor.BEAN.set(JsScheduledExecutor())

    val transport = JsHttpTransport(baseUrl)
    val config = RestConfig(transport)
    platformConfig = config
    RestRepositoryBootstrap.initialize(config, JsCryptoProvider())

    // Wire view factories — each creates a WorkerCubeView
    RootPresenter.createView = { p ->
        createWorkerView("RootView", p) { p.state }
    }
    LoginPresenter.createView = { p ->
        createWorkerView("LoginView", p) { p.state }
    }
    HomePresenter.createView = { p ->
        createWorkerView("HomeView", p) { p.state }
    }
    CartPresenter.createView = { p ->
        createWorkerView("CartView", p) { p.state }
    }
    ProductPresenter.createView = { p ->
        createWorkerView("ProductView", p) { p.state }
    }
    ReceiptPresenter.createView = { p ->
        createWorkerView("ReceiptView", p) { p.state }
    }
    ProductsPanelPresenter.createView = { p ->
        createWorkerView("ProductsPanelView", p) { p.state }
    }
    PurchasesPanelPresenter.createView = { p ->
        createWorkerView("PurchasesPanelView", p) { p.state }
    }
}

/**
 * Action dispatch map: each method name maps to an adapter that converts JS args
 * and calls the presenter method with proper Kotlin types.
 * This avoids dynamic reflection and eliminates the need for @JsName on presenters.
 */
private val actionAdapters: Map<String, (Any, dynamic) -> Unit> = mapOf(
    // LoginPresenter
    "onEnter" to { presenter, args ->
        (presenter as LoginPresenter).onEnter(args[0] as? String, args[1] as? String)
    },
    // HomePresenter / ProductsPanelPresenter
    "onOpenProduct" to { presenter, args ->
        val productId = (args[0] as? Number)?.toLong()
        when (presenter) {
            is HomePresenter -> presenter.onOpenProduct(productId)
            is ProductsPanelPresenter -> presenter.onOpenProduct(productId)
        }
    },
    // HomePresenter / PurchasesPanelPresenter
    "onOpenReceipt" to { presenter, args ->
        val purchaseId = (args[0] as? Number)?.toLong()
        when (presenter) {
            is HomePresenter -> presenter.onOpenReceipt(purchaseId)
            is PurchasesPanelPresenter -> presenter.onOpenReceipt(purchaseId)
        }
    },
    // HomePresenter
    "onOpenCart" to { presenter, _ ->
        (presenter as HomePresenter).onOpenCart()
    },
    "onExit" to { presenter, _ ->
        (presenter as HomePresenter).onExit()
    },
    // CartPresenter
    "onModifyQuantity" to { presenter, args ->
        (presenter as CartPresenter).onModifyQuantity(
            (args[0] as? Number)?.toLong(),
            (args[1] as? Number)?.toInt()
        )
    },
    "onRemoveProduct" to { presenter, args ->
        (presenter as CartPresenter).onRemoveProduct((args[0] as? Number)?.toLong())
    },
    "onBuy" to { presenter, _ ->
        (presenter as CartPresenter).onBuy()
    },
    // CartPresenter / ProductPresenter / ReceiptPresenter
    "onOpenProducts" to { presenter, _ ->
        when (presenter) {
            is CartPresenter -> presenter.onOpenProducts()
            is ProductPresenter -> presenter.onOpenProducts()
            is ReceiptPresenter -> presenter.onOpenProducts()
        }
    },
    // ProductPresenter
    "onAddToCart" to { presenter, args ->
        (presenter as ProductPresenter).onAddToCart((args[0] as? Number)?.toInt())
    },
    // ReceiptPresenter
    "onPrint" to { presenter, _ ->
        (presenter as ReceiptPresenter).onPrint()
    },
    // PurchasesPanelPresenter
    "onPageChange" to { presenter, args ->
        (presenter as PurchasesPanelPresenter).onPageChange((args[0] as? Number)?.toInt() ?: 0)
    },
    "onItemSizeCapacityChanged" to { presenter, args ->
        (presenter as PurchasesPanelPresenter).onItemSizeCapacityChanged((args[0] as? Number)?.toInt() ?: 0)
    },
)

private fun handleAction(viewId: String, method: String, args: dynamic) {
    val presenter = viewRegistry[viewId]
    if (presenter == null) {
        LOG.error("No presenter found for viewId=$viewId")
        return
    }

    val adapter = actionAdapters[method]
    if (adapter == null) {
        LOG.error("No action adapter for method '$method' on viewId=$viewId")
        return
    }

    try {
        adapter(presenter, args)
    } catch (e: Exception) {
        LOG.error("Error dispatching action $method on $viewId", e)
        app.alertUnexpectedError(LOG, "Erro em $viewId.$method", e)
    }
}

private fun handleNavigate(path: String?) {
    val intent = CubeIntent.parse(path ?: "")
    if (intent.place == null) {
        intent.place = app.getRootPlace()
    }

    val actualSignature = (intent.removeParameter("sign") as? String) ?: ""
    val intentStr = intent.toString()

    if (verifyIntent(intentStr, actualSignature)) {
        app.go(intent)
    } else {
        app.updateHistory()
        val newIntent = app.newIntent()
        if (newIntent.place == null) {
            newIntent.place = app.getRootPlace()
        }
        app.go(newIntent)
    }
}

fun main() {
    js("self").onmessage = { event: dynamic ->
        val data = event.data
        when (data.type as? String) {
            "init" -> {
                val baseUrl = data.baseUrl as String
                initializePlatform(baseUrl)

                app = WorkerShoppingApplication()
                WorkerUpdateScheduler.initialize { app }

                platformConfig.transport.onAuthFailure = {
                    platformConfig.authClient?.clearTokens()
                    app.sessionStorage.remove("authState")
                    app.sessionStorage.remove("securityContext")
                    app.setSecurityContext(null)
                    app.go("public")
                    WorkerUpdateScheduler.flush()
                }

                // Ensure route registrations
                Routes.Place.entries

                // Navigate to initial path
                val hash = data.hash as? String
                if (!hash.isNullOrBlank()) {
                    handleNavigate(hash)
                } else {
                    app.go("public")
                }

                WorkerUpdateScheduler.flush()

                val readyMsg = js("{}")
                readyMsg.type = "ready"
                js("self").postMessage(readyMsg)
            }

            "action" -> {
                val viewId = data.viewId as String
                val method = data.method as String
                val args = data.args
                handleAction(viewId, method, args)
                WorkerUpdateScheduler.flush()
            }

            "navigate" -> {
                val path = data.path as? String
                handleNavigate(path)
                WorkerUpdateScheduler.flush()
            }

            "hashChange" -> {
                val hash = data.hash as? String
                if (!hash.isNullOrBlank() && hash != app.fragment) {
                    handleNavigate(hash)
                    WorkerUpdateScheduler.flush()
                }
            }
        }
        Unit
    }
}
