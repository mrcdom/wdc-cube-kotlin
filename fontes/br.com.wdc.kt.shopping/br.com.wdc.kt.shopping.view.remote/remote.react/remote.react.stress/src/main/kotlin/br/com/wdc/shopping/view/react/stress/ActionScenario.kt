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
}

/**
 * View IDs as defined in the skeleton viewimpls.
 */
object ViewIds {
    const val BROWSER = "7b32e816a191:0"
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

        // 2. Browse products panel (already on home after login)
        // Pick a random product from the products panel state
        val productId = pickProductId(client) ?: return true // no products available

        // 3. Open product detail
        if (!doOpenProduct(client, metrics, productId)) return false

        // 4. Add to cart
        if (!doAddToCart(client, metrics)) return false

        // 5. Go back to products
        if (!doOpenProducts(client, metrics, ViewIds.PRODUCT)) return false

        // 6. Open cart
        if (!doOpenCart(client, metrics)) return false

        // 7. Buy
        if (!doBuy(client, metrics)) return false

        // 8. Return to products from receipt
        if (!doOpenProducts(client, metrics, ViewIds.RECEIPT)) return false

        // 9. Logout
        if (!doExit(client, metrics)) return false

        return true
    }

    private fun doLogin(client: VirtualClient, metrics: MetricsCollector): Boolean {
        val loginViewId = findViewId(client, ViewIds.LOGIN) ?: ViewIds.LOGIN + ":1"
        val formData = mapOf(
            "userName" to credentials.userName,
            "password" to client.cipherField(credentials.password),
        )
        val latency = client.sendEvent(loginViewId, 1, formData)
        return recordAction(metrics, "login", latency)
    }

    private fun doOpenProduct(client: VirtualClient, metrics: MetricsCollector, productId: Long): Boolean {
        val viewId = findViewId(client, ViewIds.PRODUCTS_PANEL) ?: return false
        val formData = mapOf("p.productId" to productId)
        val latency = client.sendEvent(viewId, 1, formData)
        return recordAction(metrics, "openProduct", latency)
    }

    private fun doAddToCart(client: VirtualClient, metrics: MetricsCollector): Boolean {
        val viewId = findViewId(client, ViewIds.PRODUCT) ?: return false
        val formData = mapOf("p.quantity" to 1)
        val latency = client.sendEvent(viewId, 2, formData)
        return recordAction(metrics, "addToCart", latency)
    }

    private fun doOpenProducts(client: VirtualClient, metrics: MetricsCollector, fromViewVid: String): Boolean {
        val viewId = findViewId(client, fromViewVid) ?: return false
        val latency = client.sendEvent(viewId, 1)
        return recordAction(metrics, "openProducts", latency)
    }

    private fun doOpenCart(client: VirtualClient, metrics: MetricsCollector): Boolean {
        val viewId = findViewId(client, ViewIds.HOME) ?: return false
        val latency = client.sendEvent(viewId, 2)
        return recordAction(metrics, "openCart", latency)
    }

    private fun doBuy(client: VirtualClient, metrics: MetricsCollector): Boolean {
        val viewId = findViewId(client, ViewIds.CART) ?: return false
        val latency = client.sendEvent(viewId, 1)
        return recordAction(metrics, "buy", latency)
    }

    private fun doExit(client: VirtualClient, metrics: MetricsCollector): Boolean {
        val viewId = findViewId(client, ViewIds.HOME) ?: return false
        val latency = client.sendEvent(viewId, 1)
        return recordAction(metrics, "exit", latency)
    }

    private fun findViewId(client: VirtualClient, vid: String): String? {
        // Search view states for the matching VID prefix
        for (key in client.getViewState(vid).keys) {
            // If the exact key exists, return it
        }
        // Try with instanceId suffix patterns
        return vid + ":1"  // Default instanceId
    }

    private fun pickProductId(client: VirtualClient): Long? {
        // Try to extract product IDs from the products panel state
        val state = client.getViewState(findViewId(client, ViewIds.PRODUCTS_PANEL) ?: return null)
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
        val loginViewId = findViewId(client, ViewIds.LOGIN)
        val formData = mapOf(
            "userName" to credentials.userName,
            "password" to client.cipherField(credentials.password),
        )
        val loginLatency = client.sendEvent(loginViewId, 1, formData)
        if (!recordAction(metrics, "login", loginLatency)) return false

        // 2. Paginate purchases (pages 0..2)
        val purchasesViewId = findViewId(client, ViewIds.PURCHASES_PANEL)
        for (page in 0..2) {
            val latency = client.sendEvent(purchasesViewId, 2, mapOf("p.page" to page))
            if (!recordAction(metrics, "pageChange", latency)) return false
        }

        // 3. Open a product
        val productsViewId = findViewId(client, ViewIds.PRODUCTS_PANEL)
        val latency = client.sendEvent(productsViewId, 1, mapOf("p.productId" to 1L))
        if (!recordAction(metrics, "openProduct", latency)) return false

        // 4. Return to products
        val productViewId = findViewId(client, ViewIds.PRODUCT)
        val backLatency = client.sendEvent(productViewId, 1)
        if (!recordAction(metrics, "openProducts", backLatency)) return false

        // 5. Logout
        val homeViewId = findViewId(client, ViewIds.HOME)
        val exitLatency = client.sendEvent(homeViewId, 1)
        if (!recordAction(metrics, "exit", exitLatency)) return false

        return true
    }

    private fun findViewId(client: VirtualClient, vid: String): String {
        return vid + ":1"
    }
}

/**
 * Rapid navigation scenario: Login → rapidly switch between screens
 * Tests navigation/routing performance under pressure.
 */
class RapidNavigationScenario(private val credentials: UserCredentials) : ActionScenario("rapid-navigation") {

    override fun execute(client: VirtualClient, metrics: MetricsCollector): Boolean {
        // 1. Login
        val loginViewId = ViewIds.LOGIN + ":1"
        val formData = mapOf(
            "userName" to credentials.userName,
            "password" to client.cipherField(credentials.password),
        )
        val loginLatency = client.sendEvent(loginViewId, 1, formData)
        if (!recordAction(metrics, "login", loginLatency)) return false

        // 2. Rapid open product → back → open product → back (5 times)
        val productsViewId = ViewIds.PRODUCTS_PANEL + ":1"
        val productViewId = ViewIds.PRODUCT + ":1"

        repeat(5) {
            val productId = (it + 1).toLong()
            val openLatency = client.sendEvent(productsViewId, 1, mapOf("p.productId" to productId))
            if (!recordAction(metrics, "openProduct", openLatency)) return false

            val backLatency = client.sendEvent(productViewId, 1)
            if (!recordAction(metrics, "openProducts", backLatency)) return false
        }

        // 3. Open cart (even if empty)
        val homeViewId = ViewIds.HOME + ":1"
        val cartLatency = client.sendEvent(homeViewId, 2)
        if (!recordAction(metrics, "openCart", cartLatency)) return false

        // 4. Back to products from cart
        val cartViewId = ViewIds.CART + ":1"
        val backLatency = client.sendEvent(cartViewId, 3)
        if (!recordAction(metrics, "openProducts", backLatency)) return false

        // 5. Logout
        val exitLatency = client.sendEvent(homeViewId, 1)
        if (!recordAction(metrics, "exit", exitLatency)) return false

        return true
    }
}
