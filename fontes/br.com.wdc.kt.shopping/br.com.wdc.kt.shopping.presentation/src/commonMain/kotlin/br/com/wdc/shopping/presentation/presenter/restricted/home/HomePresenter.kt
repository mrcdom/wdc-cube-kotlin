package br.com.wdc.shopping.presentation.presenter.restricted.home

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.CubeViewSlot
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.exception.ProductNotFoundException
import br.com.wdc.shopping.presentation.exception.PurchaseNotFoundException
import br.com.wdc.shopping.presentation.presenter.open.login.LoginKeys
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartKeys
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartManager
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductKeys
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptKeys

class HomePresenter(app: ShoppingApplication) : AbstractCubePresenter<ShoppingApplication>(app) {

    companion object {
        private val LOG = Log.getLogger("HomePresenter")
        private val NOOP: () -> Unit = {}

        var createView: ((HomePresenter) -> CubeView)? = null
    }

    // :: Public Instance Fields

    val state = observed(HomeViewState())

    // :: Internal Instance Fields

    private val contentSlot = CubeViewSlot { v -> setContentView(v) }

    private var ownerSlot: CubeViewSlot? = null
    private var cart: CartManager? = null
    private var productsPanel: ProductsPanelPresenter? = null
    private var purchasesPanel: PurchasesPanelPresenter? = null
    private var onCartCommitListenerRemover: () -> Unit = NOOP
    private var onCartChangeListenerRemover: () -> Unit = NOOP

    // :: Cube API

    override fun release() {
        state.contentView = null

        productsPanel?.let {
            it.release()
            productsPanel = null
            state.productsPanelView = null
        }

        purchasesPanel?.let {
            it.release()
            purchasesPanel = null
            state.purchasesPanelView = null
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

            state.nickName = app.subject?.nickName

            productsPanel = ProductsPanelPresenter(app, this)
            state.productsPanelView = productsPanel!!.initialize()

            purchasesPanel = PurchasesPanelPresenter(app, this)
            state.purchasesPanelView = purchasesPanel!!.initialize()

            cart = CartManager(app.getPurchaseRepository())
            onCartCommitListenerRemover = cart!!.addCommitListener(::onCartCommited)
            onCartChangeListenerRemover = cart!!.addChangeListener(::onCartChanged)
            app.cart = cart
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
        HomeKeys(app, intent).purchasesPage = purchasesPanel?.state?.page ?: 0
    }

    // :: User Actions

    private suspend fun onCartCommited() {
        productsPanel?.loadProducts()
        purchasesPanel?.onPageChange(0)
    }

    private fun onCartChanged() {
        state.cartItemCount = cart?.getItemCount() ?: 0
    }

    suspend fun onOpenReceipt(purchaseId: Long?) {
        try {
            if (purchaseId == null || purchaseId <= 0) {
                alertPurchaseIdRequired()
                LOG.warn("onOpenReceipt: {}", state.errorMessage)
                return
            }

            ReceiptKeys(app).apply { this.purchaseId = purchaseId }.flip()
        } catch (caught: Exception) {
            if (caught is PurchaseNotFoundException) {
                alertPurchaseNotFound()
                LOG.warn("{}: purchaseId={}", state.errorMessage, purchaseId)
                return
            }

            app.alertUnexpectedError(
                LOG,
                "Trying to go to receipt place to show purchaseId=$purchaseId",
                caught
            )
        }
    }

    suspend fun onOpenProduct(productId: Long?) {
        try {
            if (productId == null || productId <= 0) {
                alertProductIdRequired()
                LOG.warn("onOpenProduct: {}", state.errorMessage)
                return
            }

            ProductKeys(app).apply { this.productId = productId }.flip()
        } catch (caught: Exception) {
            if (caught is ProductNotFoundException) {
                alertProductNotFound()
                LOG.warn("{}: productId={}", state.errorMessage, productId)
                return
            }

            app.alertUnexpectedError(
                LOG,
                "Trying to go to product place to show productId=$productId",
                caught
            )
        }
    }

    suspend fun onOpenCart() {
        try {
            CartKeys(app).flip()
        } catch (caught: Exception) {
            app.alertUnexpectedError(LOG, "Trying to go to cart place", caught)
        }
    }

    suspend fun onExit() {
        try {
            cart!!.clear()
            app.subject = null
            app.setSecurityContext(null)
            app.sessionStorage.clear()
            setContentView(null)
            reloadPurchasesIfNeeded()

            LoginKeys(app).flip()
        } catch (caught: Exception) {
            app.alertUnexpectedError(LOG, "Trying to go to login place", caught)
        }
    }

    // :: Messages

    private fun alertProductNotFound() {
        state.errorCode = 3
        state.errorMessage = "Product code not found."
    }

    private fun alertPurchaseNotFound() {
        state.errorCode = 5
        state.errorMessage = "Receipt code not found."
    }

    private fun alertProductIdRequired() {
        state.errorCode = 6
        state.errorMessage = "Product code is a required argument."
    }

    private fun alertPurchaseIdRequired() {
        state.errorCode = 7
        state.errorMessage = "Receipt code is a required argument."
    }

    // :: Slots

    private fun setContentView(view: CubeView?) {
        if (state.contentView !== view) {
            state.contentView = view
        }
    }

    private suspend fun reloadPurchasesIfNeeded() {
        purchasesPanel?.loadPurchases()
    }
}
