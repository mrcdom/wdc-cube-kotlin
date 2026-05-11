package br.com.wdc.shopping.presentation.presenter.restricted.home

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.CubeViewSlot
import br.com.wdc.shopping.presentation.PlaceAttributes
import br.com.wdc.shopping.presentation.PlaceParameters
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.exception.ProductNotFoundException
import br.com.wdc.shopping.presentation.presenter.Routes
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartManager
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelPresenter

class HomePresenter(app: ShoppingApplication) : AbstractCubePresenter<ShoppingApplication>(app) {

    companion object {
        private val LOG = Log.getLogger(HomePresenter::class.java)
        private val NOOP: () -> Unit = {}

        @JvmField
        var createView: ((HomePresenter) -> CubeView)? = null
    }

    // :: Public Instance Fields

    val state = HomeViewState()

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

    override fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        if (app.subject == null) {
            Routes.login(app, intent)
            return false
        }

        if (initialization || view == null) {
            ownerSlot = intent.getViewSlot(PlaceAttributes.SLOT_OWNER)
            view = createView?.invoke(this)

            state.nickName = app.subject?.nickName

            productsPanel = ProductsPanelPresenter(app, this)
            state.productsPanelView = productsPanel!!.initialize()

            purchasesPanel = PurchasesPanelPresenter(app, this)
            state.purchasesPanelView = purchasesPanel!!.initialize()

            cart = CartManager(app)
            onCartCommitListenerRemover = cart!!.addCommitListener(::onCartCommited)
            onCartChangeListenerRemover = cart!!.addChangeListener(::onCartChanged)
            app.cart = cart
            update()
        }

        if (ownerSlot == null) {
            return false
        }

        ownerSlot!!.setView(view!!)

        if (deepest) {
            setContentView(null)
        } else {
            intent.setViewSlot(PlaceAttributes.SLOT_OWNER, contentSlot)
        }

        return true
    }

    override fun publishParameters(intent: CubeIntent) {
        // NOOP
    }

    override fun commitComputedState() {
        val newCartItemCount = cart?.getItemCount() ?: 0
        if (state.cartItemCount != newCartItemCount) {
            state.cartItemCount = newCartItemCount
            update()
        }
    }

    // :: User Actions

    private fun onCartCommited() {
        productsPanel?.loadProducts()
        purchasesPanel?.onPageChange(0)
    }

    private fun onCartChanged() {
        state.cartItemCount = cart!!.getItemCount()
        update()
    }

    fun onOpenReceipt(purchaseId: Long?) {
        try {
            if (purchaseId == null) {
                alertPurchaseIdRequired()
                LOG.warn("onOpenReceipt: {}", state.errorMessage)
                return
            }

            val intent = app.newIntent()
            intent.setParameter(PlaceParameters.PURCHASE_ID, purchaseId)
            Routes.receipt(app, intent)
        } catch (caught: Exception) {
            if (caught is ProductNotFoundException) {
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

    fun onOpenProduct(productId: Long?) {
        try {
            if (productId == null) {
                alertProductIdRequired()
                LOG.warn("onOpenProduct: {}", state.errorMessage)
                return
            }

            val intent = app.newIntent()
            intent.setParameter(PlaceParameters.PRODUCT_ID, productId)
            Routes.product(app, intent)
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

    fun onOpenCart() {
        try {
            Routes.cart(app)
        } catch (caught: Exception) {
            app.alertUnexpectedError(LOG, "Trying to go to cart place", caught)
        }
    }

    fun onExit() {
        try {
            cart!!.clear()
            app.subject = null
            setContentView(null)

            Routes.login(app)
        } catch (caught: Exception) {
            app.alertUnexpectedError(LOG, "Trying to go to login place", caught)
        }
    }

    // :: Messages

    private fun alertProductNotFound() {
        state.errorCode = 3
        state.errorMessage = "Código do produto não localizado."
        update()
    }

    private fun alertPurchaseNotFound() {
        state.errorCode = 5
        state.errorMessage = "Código do recibo não localizado."
        update()
    }

    private fun alertProductIdRequired() {
        state.errorCode = 6
        state.errorMessage = "Código do produto é um argumento obrigatório."
        update()
    }

    private fun alertPurchaseIdRequired() {
        state.errorCode = 7
        state.errorMessage = "Código do recibo é um argumento obrigatório."
        update()
    }

    // :: Slots

    private fun setContentView(view: CubeView?) {
        if (state.contentView !== view) {
            state.contentView = view
            update()

            // Reload purchases when returning to the main view
            if (view == null) {
                purchasesPanel?.loadPurchases()
            }
        }
    }
}
