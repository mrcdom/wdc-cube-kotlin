package br.com.wdc.shopping.presentation.presenter.restricted.home

import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.CubeViewSlot
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.exception.ProductNotFoundException
import br.com.wdc.shopping.presentation.exception.PurchaseNotFoundException
import br.com.wdc.shopping.presentation.presenter.open.login.LoginKeys
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartKeys
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartManager
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductKeys
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductService
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptKeys

class HomePresenter(app: RootPresenter) : AbstractCubePresenter<RootPresenter>(app) {

    companion object {
        private val LOG = Log.getLogger("HomePresenter")
        private val NOOP: () -> Unit = {}

        /** The session entry that keeps the cart across page reloads. */
        const val CART_KEY = "cart"

        var createView: ((HomePresenter) -> CubeView)? = null
    }

    // :: Public Instance Fields

    val scope = bind(HomeScope())

    // :: Internal Instance Fields

    private val contentSlot = CubeViewSlot { v -> setContentView(v) }

    private var ownerSlot: CubeViewSlot? = null
    private var cart: CartManager? = null
    private var productsPanel: ProductsPanelPresenter? = null
    private var purchasesPanel: PurchasesPanelPresenter? = null
    private var onCartCommitListenerRemover: () -> Unit = NOOP
    private var onCartChangeListenerRemover: () -> Unit = NOOP

    init {
        scope.onExit = action(1) { exit() }
        scope.onOpenCart = action(2) { CartKeys(app).flip() }
    }

    // :: Cube API

    override fun release() {
        scope.contentView = null

        productsPanel?.let {
            it.release()
            productsPanel = null
            scope.productsPanelView = null
        }

        purchasesPanel?.let {
            it.release()
            purchasesPanel = null
            scope.purchasesPanelView = null
        }

        app.cart = null

        onCartCommitListenerRemover()
        onCartCommitListenerRemover = NOOP

        onCartChangeListenerRemover()
        onCartChangeListenerRemover = NOOP

        view?.release()
        view = null
    }

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        if (app.subject == null) {
            LoginKeys(app, intent).flip() // redirect, with the intent in transit
            return false
        }

        // read on every call: a reused presenter goes where its parent puts it now, not where it was first put
        ownerSlot = HomeKeys(app, intent).parentSlot
        if (initialization || view == null) {
            view = createView?.invoke(this)

            scope.nickName = app.subject?.nickName

            productsPanel = ProductsPanelPresenter(app, this)
            scope.productsPanelView = productsPanel!!.initialize()

            purchasesPanel = PurchasesPanelPresenter(app, this)
            scope.purchasesPanelView = purchasesPanel!!.initialize()

            cart = CartManager(PurchaseRepository.BEAN.get())
            restoreCart()
            onCartCommitListenerRemover = cart!!.addCommitListener(::onCartCommited)
            onCartChangeListenerRemover = cart!!.addChangeListener(::onCartChanged)
            app.cart = cart
            scope.cartItemCount = cart!!.getItemCount()
        }

        if (ownerSlot == null) {
            return false
        }

        ownerSlot!!.setView(view!!)

        // the page the address asks for; the panel loads it now, or once its view tells its capacity
        purchasesPanel?.restorePage(HomeKeys(app, intent).purchasesPage)

        if (deepest) {
            setContentView(null)
            reloadPurchasesIfNeeded()
        } else {
            HomeKeys(app, intent).parentSlot = contentSlot
        }

        return true
    }

    override fun publishParameters(intent: CubeIntent) {
        HomeKeys(app, intent).purchasesPage = purchasesPanel?.scope?.page ?: 0
    }

    // :: User Actions

    private suspend fun onCartCommited() {
        productsPanel?.loadProducts()
        purchasesPanel?.changePage(0)
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
        val products = ProductService(ProductRepository.BEAN.get())
        for ((productId, quantity) in snapshot.quantities) {
            try {
                cart!!.addProduct(products.loadProductById(productId), quantity)
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
    internal suspend fun openReceipt(purchaseId: Long?) {
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
    internal suspend fun openProduct(productId: Long?) {
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
        setContentView(null)
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

    private fun setContentView(view: CubeView?) {
        if (scope.contentView !== view) {
            scope.contentView = view
        }
    }

    private suspend fun reloadPurchasesIfNeeded() {
        purchasesPanel?.loadPurchases()
    }
}
