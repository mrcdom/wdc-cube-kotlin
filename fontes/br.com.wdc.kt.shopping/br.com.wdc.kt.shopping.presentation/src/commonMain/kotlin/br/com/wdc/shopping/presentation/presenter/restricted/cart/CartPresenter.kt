package br.com.wdc.shopping.presentation.presenter.restricted.cart

import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.function.Registration
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.CubeViewSlot
import br.com.wdc.shopping.domain.exception.InvalidCartItemException
import br.com.wdc.shopping.domain.exception.OfflineException
import br.com.wdc.shopping.presentation.PlaceAttributes
import br.com.wdc.shopping.presentation.PlaceParameters
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.Routes
import kotlin.time.Duration.Companion.seconds

class CartPresenter(app: ShoppingApplication) : AbstractCubePresenter<ShoppingApplication>(app) {

    companion object {
        private val LOG = Log.getLogger("CartPresenter")

        var createView: ((CartPresenter) -> CubeView)? = null
    }

    // :: Public Instance Fields

    val state = observed(CartViewState())

    // :: Internal Instance Fields

    private val cart: CartManager = app.cart!!
    private var ownerSlot: CubeViewSlot? = null
    private var pendingErrorClear: Registration? = null

    init {
        state.items = emptyList()
    }

    // :: Cube API

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        state.items = cart.getCartItems()

        // read on every call: a reused presenter goes where its parent puts it now, not where it was first put
        ownerSlot = intent.getViewSlot(PlaceAttributes.SLOT_OWNER)
        if (initialization) {
            view = createView?.invoke(this)
        }

        ownerSlot?.setView(view!!)

        return true
    }

    // :: User Actions

    fun onModifyQuantity(productId: Long?, quantity: Int?) {
        try {
            if (productId == null) {
                errorMalformedProductId()
                LOG.warn("onModifyQuantity.errorMalformedProductId: {}", state.errorMessage)
                return
            }

            if (quantity == null) {
                errorMalformedQuantity()
                LOG.warn("onModifyQuantity.errorMalformedQuantity: {}", state.errorMessage)
                return
            }

            if (quantity < 1) {
                alertThereIsItemWhichValueIsLessThanOne()
                LOG.warn("onModifyQuantity.alertThereIsItemWhichValueIsLessThanOne: {}", state.errorMessage)
                return
            }

            val found = cart.modifyProductQuantity(productId, quantity)
            if (!found) {
                alertProductNotFound()
                LOG.warn("onModifyQuantity.alertProductNotFound: {}", state.errorMessage)
            } else {
                state.items = cart.getCartItems()
            }
        } catch (caught: Exception) {
            app.alertUnexpectedError(LOG, "Modifying a product quantity", caught)
        }
    }

    suspend fun onRemoveProduct(productId: Long?) {
        try {
            if (productId == null) {
                errorMalformedProductId()
                LOG.warn("onRemoveProduct: {}", state.errorMessage)
                return
            }

            val modified = cart.removeProduct(productId)
            if (modified) {
                if (cart.getSize() == 0) {
                    Routes.home(app)
                } else {
                    state.items = cart.getCartItems()
                }
            }
        } catch (caught: Exception) {
            app.alertUnexpectedError(LOG, "Removing a product", caught)
        }
    }

    suspend fun onBuy() {
        try {
            if (cart.getSize() == 0) {
                alertPurchaseOfEmptyCart()
                LOG.warn("onBuy: {}", state.errorMessage)
                return
            }

            val purchaseId = cart.commit(app.subject!!)

            val intent = app.newIntent()
            intent.setParameter(PlaceParameters.PURCHASE_ID, purchaseId)
            intent.setAttribute(PlaceAttributes.ATTR_PURCHASE_MADE, true)
            Routes.receipt(app, intent)
        } catch (caught: Exception) {
            if (caught is InvalidCartItemException) {
                alertThereIsItemWhichValueIsLessThanOne()
                LOG.error("onBuy.alertThereIsItemWhichValueIsLessThanOne: {}", state.errorMessage, caught)
                return
            }

            if (caught is OfflineException) {
                alertDatabaseOffline()
                LOG.error("onBuy.alertDatabaseOffline: {}", state.errorMessage, caught)
                return
            }

            app.alertUnexpectedError(LOG, "Buying a product", caught)
        }
    }

    suspend fun onOpenProducts() {
        try {
            Routes.home(app)
        } catch (caught: Exception) {
            app.alertUnexpectedError(LOG, "Going to root restricted place", caught)
        }
    }

    // :: Message Methods

    private fun clearErrorAfterDelay() {
        pendingErrorClear?.remove()
        pendingErrorClear = ScheduledExecutor.BEAN.get().schedule({
            state.errorCode = 0
            state.errorMessage = null
        }, 3.seconds)
    }

    private fun alertThereIsItemWhichValueIsLessThanOne() {
        state.errorCode = 1
        state.errorMessage = "Some products have a quantity of less than one item. Unable to purchase."
        clearErrorAfterDelay()
    }

    private fun alertProductNotFound() {
        state.errorCode = 2
        state.errorMessage = "Product not found in the database."
        clearErrorAfterDelay()
    }

    private fun alertPurchaseOfEmptyCart() {
        state.errorCode = 3
        state.errorMessage = "There must be at least one item in the cart to complete a purchase."
        clearErrorAfterDelay()
    }

    private fun alertDatabaseOffline() {
        state.errorCode = 4
        state.errorMessage = "The database is currently offline. Please wait a moment and try again."
        clearErrorAfterDelay()
    }

    private fun errorMalformedProductId() {
        state.errorCode = 5
        state.errorMessage = "Malformed product code."
        clearErrorAfterDelay()
    }

    private fun errorMalformedQuantity() {
        state.errorCode = 6
        state.errorMessage = "Malformed quantity value."
        clearErrorAfterDelay()
    }
}
