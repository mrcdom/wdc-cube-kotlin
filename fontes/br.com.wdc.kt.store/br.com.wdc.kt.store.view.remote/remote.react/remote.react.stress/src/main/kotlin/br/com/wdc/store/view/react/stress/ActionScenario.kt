package br.com.wdc.store.view.react.stress

import br.com.wdc.store.presentation.presenter.open.login.LoginScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartItemScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.store.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.store.presentation.presenter.restricted.home.ProductsPanelScope
import br.com.wdc.store.presentation.presenter.restricted.home.PurchasesPanelScope
import br.com.wdc.store.presentation.presenter.restricted.products.ProductScope
import br.com.wdc.store.presentation.presenter.restricted.receipt.ReceiptScope
import br.com.wdc.framework.cube.remote.BrowserScope
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
     * Resolves the full instance ID for a VID by searching the client's tracked scopes.
     * Returns null if the view is not currently present in state.
     */
    protected fun resolveView(client: VirtualClient, vid: String): String? {
        val instanceId = client.findViewByVid(vid)
        if (instanceId == null) {
            LOG.warn("View VID '{}' not found in client state (available: {})", vid, client.getTrackedViewIds())
        }
        return instanceId
    }

    /**
     * Logs in from the Login view. A scenario that failed midway leaves the session
     * authenticated, so log out first when the Home view is still on screen.
     */
    protected fun login(client: VirtualClient, metrics: MetricsCollector, credentials: UserCredentials): Boolean {
        if (client.findViewByVid(Vid.LOGIN) == null) {
            val homeViewId = client.findViewByVid(Vid.HOME)
            if (homeViewId != null && !recordAction(metrics, "exit", client.sendEvent(homeViewId, 1))) return false
        }
        val viewId = resolveView(client, Vid.LOGIN) ?: return false
        val formData = mapOf(
            "userName" to credentials.userName,
            "password" to client.cipherField(credentials.password),
        )
        val latency = client.sendEvent(viewId, 1, formData)
        return recordAction(metrics, "login", latency)
    }

    /**
     * Product IDs currently listed by the products panel (IDs are not contiguous).
     */
    protected fun productIds(client: VirtualClient): List<Long> {
        val scope = client.getScopeByVid(Vid.PRODUCTS_PANEL)
        @Suppress("UNCHECKED_CAST")
        val products = scope["products"] as? List<Map<String, Any?>> ?: return emptyList()
        return products.mapNotNull { (it["id"] as? Number)?.toLong() }
    }
}

/**
 * View type identifiers (VIDs): each scope declares the one of the client component that paints it.
 * Instance IDs are assigned dynamically at runtime as "{vid}:{sequentialNum}".
 */
object Vid {
    const val BROWSER = BrowserScope.VID
    const val LOGIN = LoginScope.VID
    const val HOME = HomeScope.VID
    const val PRODUCTS_PANEL = ProductsPanelScope.VID
    const val PURCHASES_PANEL = PurchasesPanelScope.VID
    const val PRODUCT = ProductScope.VID
    const val CART = CartScope.VID
    const val CART_ITEM = CartItemScope.VID
    const val RECEIPT = ReceiptScope.VID
}

/**
 * Full shopping journey: Login → View Product → Add to Cart → Buy → View Receipt → Return → Logout
 */
class FullShoppingScenario(private val credentials: UserCredentials) : ActionScenario("full-shopping") {

    override fun execute(client: VirtualClient, metrics: MetricsCollector): Boolean {
        // 1. Login
        if (!login(client, metrics, credentials)) return false

        // 2. Pick a random product from the products panel state
        val productId = pickProductId(client) ?: return true

        // 3. Open product detail
        if (!doOpenProduct(client, metrics, productId)) return false

        // 4. Add to cart (the application navigates straight to the cart)
        if (!doAddToCart(client, metrics)) return false

        // 4b. One more of it, from the cart row
        if (!doIncrementCartRow(client, metrics)) return false

        // 5. Buy
        if (!doBuy(client, metrics)) return false

        // 6. Return to products from receipt
        if (!doOpenProducts(client, metrics, Vid.RECEIPT)) return false

        // 7. Logout
        if (!doExit(client, metrics)) return false

        return true
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

    private fun doIncrementCartRow(client: VirtualClient, metrics: MetricsCollector): Boolean {
        val rowViewId = resolveView(client, Vid.CART_ITEM) ?: return false
        val latency = client.sendEvent(rowViewId, 2, mapOf("p.quantity" to 2))
        return recordAction(metrics, "modifyQuantity", latency)
    }

    private fun doOpenProducts(client: VirtualClient, metrics: MetricsCollector, fromVid: String): Boolean {
        val viewId = resolveView(client, fromVid) ?: return false
        val latency = client.sendEvent(viewId, 1)
        return recordAction(metrics, "openProducts", latency)
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
        val ids = productIds(client)
        if (ids.isNotEmpty()) {
            return ids[Random.nextInt(ids.size)]
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
        if (!login(client, metrics, credentials)) return false

        // 2. Report the panel capacity, as the browser does on layout; until then the
        //    server has no page size and answers nothing to page changes
        val panelViewId = resolveView(client, Vid.PURCHASES_PANEL) ?: return false
        val capacityLatency = client.sendEvent(panelViewId, 3, mapOf("p.capacity" to 5))
        if (!recordAction(metrics, "capacityChange", capacityLatency)) return false

        // 3. Paginate purchases (pages 0..2)
        for (page in 0..2) {
            val purchasesViewId = resolveView(client, Vid.PURCHASES_PANEL) ?: return false
            val latency = client.sendEvent(purchasesViewId, 2, mapOf("p.page" to page))
            if (!recordAction(metrics, "pageChange", latency)) return false
        }

        // 4. Open a product
        val productsViewId = resolveView(client, Vid.PRODUCTS_PANEL) ?: return false
        val latency = client.sendEvent(productsViewId, 1, mapOf("p.productId" to 1L))
        if (!recordAction(metrics, "openProduct", latency)) return false

        // 5. Return to products
        val productViewId = resolveView(client, Vid.PRODUCT) ?: return false
        val backLatency = client.sendEvent(productViewId, 1)
        if (!recordAction(metrics, "openProducts", backLatency)) return false

        // 6. Logout
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
        if (!login(client, metrics, credentials)) return false

        // 2. Rapid open product → back → open product → back (5 times)
        val ids = productIds(client).ifEmpty { listOf(1L) }
        repeat(5) {
            val productsViewId = resolveView(client, Vid.PRODUCTS_PANEL) ?: return false
            val productId = ids[it % ids.size]
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
