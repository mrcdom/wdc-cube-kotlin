package br.com.wdc.shopping.view.react.stress

import org.slf4j.LoggerFactory
import kotlin.random.Random

/**
 * Defines the sequence of user actions a VirtualClient can execute.
 * Each scenario represents a realistic user journey through the application.
 */
sealed class ActionScenario(val name: String) {

    companion object {
        private val LOG = LoggerFactory.getLogger(ActionScenario::class.java)
    }

    /**
     * Executes the scenario on the given client, recording metrics.
     * Returns true if all actions succeeded.
     */
    abstract fun execute(client: VirtualClient, metrics: MetricsCollector): Boolean

    protected fun recordAction(metrics: MetricsCollector, actionName: String, latency: Long): Boolean {
        if (latency < 0) {
            metrics.recordError(actionName)
            LOG.warn("Action '{}' failed (timeout or error)", actionName)
            return false
        }
        metrics.recordAction(actionName, latency)
        return true
    }

    /**
     * Resolves the full instance ID for a VID by searching the client's tracked view states.
     * Returns null if the view is not currently present in state.
     */
    protected fun resolveView(client: VirtualClient, vid: String): String? {
        val instanceId = client.findViewByVid(vid)
        if (instanceId == null) {
            LOG.warn("View VID '{}' not found in client state (available: {})", vid, client.getTrackedViewIds())
        }
        return instanceId
    }
}

/**
 * View type identifiers (VIDs) — fixed 12-char hex per view class.
 * Instance IDs are assigned dynamically at runtime as "{vid}:{sequentialNum}".
 */
object Vid {
    const val BROWSER = "7b32e816a191"
    const val LOGIN = "c677cda52d14"
    const val HOME = "473dbdd7a36a"
    const val PRODUCTS_PANEL = "a1b2c3d4e5f6"
    const val PURCHASES_PANEL = "b3c4d5e6f7a8"
    const val PRODUCT = "48b693f67410"
    const val CART = "7eb485e5f843"
    const val RECEIPT = "e8d0bd8ae3bc"
}

/**
 * Full shopping journey: Login → Browse → View Product → Add to Cart → Buy → View Receipt → Return
 */
class FullShoppingScenario(private val credentials: UserCredentials) : ActionScenario("full-shopping") {

    override fun execute(client: VirtualClient, metrics: MetricsCollector): Boolean {
        // 1. Login
        if (!doLogin(client, metrics)) return false

        // 2. Pick a random product from the products panel state
        val productId = pickProductId(client) ?: return true

        // 3. Open product detail
        if (!doOpenProduct(client, metrics, productId)) return false

        // 4. Add to cart
        if (!doAddToCart(client, metrics)) return false

        // 5. Go back to products
        if (!doOpenProducts(client, metrics, Vid.PRODUCT)) return false

        // 6. Open cart
        if (!doOpenCart(client, metrics)) return false

        // 7. Buy
        if (!doBuy(client, metrics)) return false

        // 8. Return to products from receipt
        if (!doOpenProducts(client, metrics, Vid.RECEIPT)) return false

        // 9. Logout
        if (!doExit(client, metrics)) return false

        return true
    }

    private fun doLogin(client: VirtualClient, metrics: MetricsCollector): Boolean {
        val viewId = resolveView(client, Vid.LOGIN) ?: return false
        val formData = mapOf(
            "userName" to credentials.userName,
            "password" to client.cipherField(credentials.password),
        )
        val latency = client.sendEvent(viewId, 1, formData)
        return recordAction(metrics, "login", latency)
    }

    private fun doOpenProduct(client: VirtualClient, metrics: MetricsCollector, productId: Long): Boolean {
        val viewId = resolveView(client, Vid.PRODUCTS_PANEL) ?: return false
        val latency = client.sendEvent(viewId, 1, mapOf("p.productId" to productId))
        return recordAction(metrics, "openProduct", latency)
    }

    private fun doAddToCart(client: VirtualClient, metrics: MetricsCollector): Boolean {
        val viewId = resolveView(client, Vid.PRODUCT) ?: return false
        val latency = client.sendEvent(viewId, 2, mapOf("p.quantity" to 1))
        return recordAction(metrics, "addToCart", latency)
    }

    private fun doOpenProducts(client: VirtualClient, metrics: MetricsCollector, fromVid: String): Boolean {
        val viewId = resolveView(client, fromVid) ?: return false
        val latency = client.sendEvent(viewId, 1)
        return recordAction(metrics, "openProducts", latency)
    }

    private fun doOpenCart(client: VirtualClient, metrics: MetricsCollector): Boolean {
        val viewId = resolveView(client, Vid.HOME) ?: return false
        val latency = client.sendEvent(viewId, 2)
        return recordAction(metrics, "openCart", latency)
    }

    private fun doBuy(client: VirtualClient, metrics: MetricsCollector): Boolean {
        val viewId = resolveView(client, Vid.CART) ?: return false
        val latency = client.sendEvent(viewId, 1)
        return recordAction(metrics, "buy", latency)
    }

    private fun doExit(client: VirtualClient, metrics: MetricsCollector): Boolean {
        val viewId = resolveView(client, Vid.HOME) ?: return false
        val latency = client.sendEvent(viewId, 1)
        return recordAction(metrics, "exit", latency)
    }

    private fun pickProductId(client: VirtualClient): Long? {
        val state = client.getViewStateByVid(Vid.PRODUCTS_PANEL)
        @Suppress("UNCHECKED_CAST")
        val products = state["products"] as? List<Map<String, Any?>>
        if (!products.isNullOrEmpty()) {
            val product = products[Random.nextInt(products.size)]
            return (product["id"] as? Number)?.toLong()
        }
        // Fallback: use product ID 1
        return 1L
    }
}

/**
 * Browse-only scenario: Login → Browse products → Paginate purchases → Logout
 * Lower intensity, tests read-heavy workload.
 */
class BrowseOnlyScenario(private val credentials: UserCredentials) : ActionScenario("browse-only") {

    override fun execute(client: VirtualClient, metrics: MetricsCollector): Boolean {
        // 1. Login
        val loginViewId = resolveView(client, Vid.LOGIN) ?: return false
        val formData = mapOf(
            "userName" to credentials.userName,
            "password" to client.cipherField(credentials.password),
        )
        val loginLatency = client.sendEvent(loginViewId, 1, formData)
        if (!recordAction(metrics, "login", loginLatency)) return false

        // 2. Paginate purchases (pages 0..2)
        for (page in 0..2) {
            val purchasesViewId = resolveView(client, Vid.PURCHASES_PANEL) ?: return false
            val latency = client.sendEvent(purchasesViewId, 2, mapOf("p.page" to page))
            if (!recordAction(metrics, "pageChange", latency)) return false
        }

        // 3. Open a product
        val productsViewId = resolveView(client, Vid.PRODUCTS_PANEL) ?: return false
        val latency = client.sendEvent(productsViewId, 1, mapOf("p.productId" to 1L))
        if (!recordAction(metrics, "openProduct", latency)) return false

        // 4. Return to products
        val productViewId = resolveView(client, Vid.PRODUCT) ?: return false
        val backLatency = client.sendEvent(productViewId, 1)
        if (!recordAction(metrics, "openProducts", backLatency)) return false

        // 5. Logout
        val homeViewId = resolveView(client, Vid.HOME) ?: return false
        val exitLatency = client.sendEvent(homeViewId, 1)
        if (!recordAction(metrics, "exit", exitLatency)) return false

        return true
    }
}

/**
 * Rapid navigation scenario: Login → rapidly switch between screens
 * Tests navigation/routing performance under pressure.
 */
class RapidNavigationScenario(private val credentials: UserCredentials) : ActionScenario("rapid-navigation") {

    override fun execute(client: VirtualClient, metrics: MetricsCollector): Boolean {
        // 1. Login
        val loginViewId = resolveView(client, Vid.LOGIN) ?: return false
        val formData = mapOf(
            "userName" to credentials.userName,
            "password" to client.cipherField(credentials.password),
        )
        val loginLatency = client.sendEvent(loginViewId, 1, formData)
        if (!recordAction(metrics, "login", loginLatency)) return false

        // 2. Rapid open product → back → open product → back (5 times)
        repeat(5) {
            val productsViewId = resolveView(client, Vid.PRODUCTS_PANEL) ?: return false
            val productId = (it + 1).toLong()
            val openLatency = client.sendEvent(productsViewId, 1, mapOf("p.productId" to productId))
            if (!recordAction(metrics, "openProduct", openLatency)) return false

            val productViewId = resolveView(client, Vid.PRODUCT) ?: return false
            val backLatency = client.sendEvent(productViewId, 1)
            if (!recordAction(metrics, "openProducts", backLatency)) return false
        }

        // 3. Open cart (even if empty)
        val homeViewId = resolveView(client, Vid.HOME) ?: return false
        val cartLatency = client.sendEvent(homeViewId, 2)
        if (!recordAction(metrics, "openCart", cartLatency)) return false

        // 4. Back to products from cart
        val cartViewId = resolveView(client, Vid.CART) ?: return false
        val backLatency = client.sendEvent(cartViewId, 3)
        if (!recordAction(metrics, "openProducts", backLatency)) return false

        // 5. Logout
        val exitLatency = client.sendEvent(homeViewId, 1)
        if (!recordAction(metrics, "exit", exitLatency)) return false

        return true
    }
}
