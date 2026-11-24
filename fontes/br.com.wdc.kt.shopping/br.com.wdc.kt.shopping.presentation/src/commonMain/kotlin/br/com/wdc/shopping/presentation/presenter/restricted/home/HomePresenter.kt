package br.com.wdc.shopping.presentation.presenter.restricted.home

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.Scope
import br.com.wdc.framework.cube.FormParam
import br.com.wdc.framework.cube.ScopeSlot
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.exception.ProductNotFoundException
import br.com.wdc.shopping.presentation.exception.PurchaseNotFoundException
import br.com.wdc.shopping.presentation.presenter.open.login.LoginKeys
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartKeys
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartManager
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductKeys
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptKeys

import kotlin.math.ceil
import kotlin.math.max
import br.com.wdc.shopping.presentation.presenter.restricted.products.calls.ProductQuery
import br.com.wdc.shopping.presentation.presenter.restricted.products.calls.ProductListQuery
import br.com.wdc.shopping.presentation.presenter.restricted.home.calls.PurchasesPageQuery

class HomePresenter(app: RootPresenter) : AbstractCubePresenter<RootPresenter>(app) {

    companion object {
        private val LOG = Log.getLogger("HomePresenter")
        private val NOOP: () -> Unit = {}

        /** The session entry that keeps the cart across page reloads. */
        const val CART_KEY = "cart"
    }

    // :: Public Instance Fields

    val scope = bind(HomeScope())

    // :: Internal Instance Fields

    private val contentSlot = ScopeSlot { setContent(it) }

    private var ownerSlot: ScopeSlot? = null
    private var cart: CartManager? = null

    // the two panels: segments of the home scope, without presenters of their own
    private val products = segment(ProductsPanelScope())
    private val purchases = segment(PurchasesPanelScope())
    private var onCartCommitListenerRemover: () -> Unit = NOOP
    private var onCartChangeListenerRemover: () -> Unit = NOOP

    init {
        scope.onExit = action(1) { exit() }
        scope.onOpenCart = action(2) { CartKeys(app).flip() }

        products.onOpenProduct = actionOf(1, FormParam.long("p.productId"), target = products) { openProduct(it) }

        // not navigations: the guard republishes the address, which follows the page
        purchases.onOpenReceipt = actionOf(1, FormParam.long("p.purchaseId"), target = purchases) { openReceipt(it) }
        purchases.onPageChange = actionOf(2, FormParam.int("p.page"), target = purchases) { changePage(it ?: 0) }
        purchases.onItemSizeCapacityChanged =
            actionOf(3, FormParam.int("p.capacity"), target = purchases) { changeCapacity(it ?: 0) }
    }

    // :: Cube API

    override fun release() {
        scope.content = null

        scope.productsPanel = null
        scope.purchasesPanel = null

        app.cart = null

        onCartCommitListenerRemover()
        onCartCommitListenerRemover = NOOP

        onCartChangeListenerRemover()
        onCartChangeListenerRemover = NOOP

        super.release()
    }

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        if (app.subject == null) {
            LoginKeys(app, intent).flip() // redirect, with the intent in transit
            return false
        }

        // read on every call: a reused presenter goes where its parent puts it now, not where it was first put
        ownerSlot = HomeKeys(app, intent).parentSlot
        if (initialization || scope.productsPanel == null) {
            scope.nickName = app.subject?.nickName

            loadProducts()
            scope.productsPanel = products
            // the purchases come with the view's first capacity
            scope.purchasesPanel = purchases

            cart = CartManager()
            restoreCart()
            onCartCommitListenerRemover = cart!!.addCommitListener(::onCartCommited)
            onCartChangeListenerRemover = cart!!.addChangeListener(::onCartChanged)
            app.cart = cart
            scope.cartItemCount = cart!!.getItemCount()
        }

        if (ownerSlot == null) {
            return false
        }

        ownerSlot!!.setScope(scope)

        // the page the address asks for; the panel loads it now, or once its view tells its capacity
        restorePage(HomeKeys(app, intent).purchasesPage)

        if (deepest) {
            setContent(null)
            reloadPurchasesIfNeeded()
        } else {
            HomeKeys(app, intent).parentSlot = contentSlot
        }

        return true
    }

    override fun publishParameters(intent: CubeIntent) {
        HomeKeys(app, intent).purchasesPage = purchases.page
    }

    // :: User Actions

    private suspend fun onCartCommited() {
        loadProducts()
        changePage(0)
    }

    private fun onCartChanged() {
        scope.cartItemCount = cart?.getItemCount() ?: 0
        saveCart()
    }

    /**
     * Restores the cart the session kept: a page reload starts the application again, not the session. The products
     * are read again, so names and prices are current; a cart kept for another user is ignored.
     */
    private suspend fun restoreCart() {
        val snapshot = CartManager.Snapshot.parse(app.sessionStorage.getString(CART_KEY)) ?: return
        if (snapshot.userId != app.subject?.id) return
        for ((productId, quantity) in snapshot.quantities) {
            try {
                cart!!.addProduct(ProductQuery(productId).run(), quantity)
            } catch (_: ProductNotFoundException) {
                LOG.info("restoreCart: product {} is no longer available", productId)
            }
        }
    }

    private fun saveCart() {
        val cart = cart ?: return
        val userId = app.subject?.id ?: return
        if (cart.getSize() == 0) {
            app.sessionStorage.remove(CART_KEY)
        } else {
            app.sessionStorage.set(CART_KEY, cart.snapshot(userId).format())
        }
    }

    /** Opens the receipt of a purchase: the purchases panel's action. */
    private suspend fun openReceipt(purchaseId: Long?) {
        if (purchaseId == null || purchaseId <= 0) {
            alertPurchaseIdRequired()
            LOG.warn("onOpenReceipt: {}", scope.errorMessage)
            return
        }

        try {
            ReceiptKeys(app).apply { this.purchaseId = purchaseId }.flip()
        } catch (caught: PurchaseNotFoundException) {
            alertPurchaseNotFound()
            LOG.warn("{}: purchaseId={}", scope.errorMessage, purchaseId)
        }
    }

    /** Opens a product: the products panel's action. */
    private suspend fun openProduct(productId: Long?) {
        if (productId == null || productId <= 0) {
            alertProductIdRequired()
            LOG.warn("onOpenProduct: {}", scope.errorMessage)
            return
        }

        try {
            ProductKeys(app).apply { this.productId = productId }.flip()
        } catch (caught: ProductNotFoundException) {
            alertProductNotFound()
            LOG.warn("{}: productId={}", scope.errorMessage, productId)
        }
    }

    private suspend fun exit() {
        cart!!.clear()
        app.subject = null
        app.sessionStorage.clear()
        setContent(null)
        reloadPurchasesIfNeeded()

        LoginKeys(app).flip()
    }

    // :: Messages

    private fun alertProductNotFound() {
        scope.errorCode = 3
        scope.errorMessage = "Product code not found."
    }

    private fun alertPurchaseNotFound() {
        scope.errorCode = 5
        scope.errorMessage = "Receipt code not found."
    }

    private fun alertProductIdRequired() {
        scope.errorCode = 6
        scope.errorMessage = "Product code is a required argument."
    }

    private fun alertPurchaseIdRequired() {
        scope.errorCode = 7
        scope.errorMessage = "Receipt code is a required argument."
    }

    // :: Slots

    private fun setContent(content: Scope?) {
        if (scope.content !== content) {
            scope.content = content
        }
    }

    private suspend fun reloadPurchasesIfNeeded() {
        loadPurchases()
    }

    // :: Products panel

    private suspend fun loadProducts() {
        try {
            products.products = ProductListQuery(limit = 1000).run()
        } catch (caught: Exception) {
            LOG.error("Failed to load products", caught)
        }
    }

    // :: Purchases panel

    /**
     * The page the address asks for. Loaded now if it changed and the view already told its capacity; otherwise with
     * the first capacity.
     */
    private suspend fun restorePage(page: Int) {
        val requested = max(0, page)
        if (requested == purchases.page) return
        purchases.page = requested
        if (purchases.pageSize > 0) loadPurchases()
    }

    private suspend fun changePage(page: Int) {
        purchases.page = max(0, page)
        loadPurchases()
    }

    private suspend fun changeCapacity(capacity: Int) {
        val newPageSize = max(1, capacity)
        if (newPageSize != purchases.pageSize) {
            // the first capacity keeps the page the address asked for; a later change goes back to the first page
            if (purchases.pageSize > 0) purchases.page = 0
            purchases.pageSize = newPageSize
            loadPurchases()
        }
    }

    private suspend fun loadPurchases() {
        val subject = app.subject
        if (subject != null && purchases.pageSize > 0) {
            var page = PurchasesPageQuery(subject.id!!, purchases.page, purchases.pageSize).run()

            purchases.totalCount = page.totalItems

            val totalPages = max(1, ceil(purchases.totalCount.toDouble() / purchases.pageSize).toInt())
            if (purchases.page >= totalPages) {
                // the requested page is past the end (the view capacity changed, or purchases disappeared): show the
                // last one, with its items — the result above is the one of the page that does not exist
                purchases.page = totalPages - 1
                page = PurchasesPageQuery(subject.id!!, purchases.page, purchases.pageSize).run()
                purchases.totalCount = page.totalItems
            }

            purchases.purchases = page.items
        }
    }
}
