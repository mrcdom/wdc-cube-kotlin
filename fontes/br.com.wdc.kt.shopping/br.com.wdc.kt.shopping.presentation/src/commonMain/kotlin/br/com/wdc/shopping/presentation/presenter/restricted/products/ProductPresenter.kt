package br.com.wdc.shopping.presentation.presenter.restricted.products

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.CubeViewSlot
import br.com.wdc.shopping.domain.exception.OfflineException
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.Routes
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeKeys

class ProductPresenter(app: ShoppingApplication) : AbstractCubePresenter<ShoppingApplication>(app) {

    companion object {
        private val LOG = Log.getLogger("ProductPresenter")

        var createView: ((ProductPresenter) -> CubeView)? = null
    }

    // :: Public Instance Fields

    val state = observed(ProductViewState())

    // :: Internal Instance Fields

    private val productService = ProductService(app.getProductRepository())
    private var ownerSlot: CubeViewSlot? = null

    // :: Cube API

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        val keys = ProductKeys(app, intent)
        val oldProductId = state.product?.id

        val newProductId = keys.productId ?: oldProductId ?: run {
            // no usable id in the address: nothing to show; go to the home, with the intent in transit
            HomeKeys(app, intent).go()
            return false
        }

        if (state.product == null || newProductId != oldProductId) {
            state.product = productService.loadProductById(newProductId)
        }

        // read on every call: a reused presenter goes where its parent puts it now, not where it was first put
        ownerSlot = keys.parentSlot
        if (initialization) {
            state.errorCode = 0
            state.errorMessage = null
            if (state.product == null) {
                throw AssertionError("Missing Product")
            }

            view = createView?.invoke(this)
        }

        ownerSlot?.setView(view!!)

        return true
    }

    override fun publishParameters(intent: CubeIntent) {
        ProductKeys(app, intent).productId = state.product?.id
    }

    // :: User Actions

    suspend fun onAddToCart(quantity: Int?) {
        try {
            if (quantity == null) {
                errorInvalidQuantity()
                LOG.warn("onAddToCart.errorInvalidQuantity: {}", state.errorMessage)
                return
            }

            if (quantity < 1) {
                alertCartItemWidthLessThanOneItem()
                LOG.warn("onAddToCart.alertCartItemWidthLessThanOneItem: {}", state.errorMessage)
                return
            }

            app.cart!!.addProduct(state.product!!, quantity)

            val intent = app.newIntent()
            Routes.cart(app, intent)
        } catch (caught: Exception) {
            if (caught is OfflineException) {
                alertDatabaseNotAvailable()
                LOG.error(state.errorMessage!!, caught)
                return
            }

            app.alertUnexpectedError(LOG, "Adding an item to cart", caught)
        }
    }

    suspend fun onOpenProducts() {
        try {
            Routes.home(app)
        } catch (caught: Exception) {
            app.alertUnexpectedError(LOG, "Going to home of restricted place", caught)
        }
    }

    // :: Messages

    private fun alertDatabaseNotAvailable() {
        state.errorCode = 1
        state.errorMessage = "The cart is not available. Please wait a moment and try again."
    }

    private fun alertCartItemWidthLessThanOneItem() {
        state.errorCode = 2
        state.errorMessage = "The quantity of items in the cart must be at least 1 (one)."
    }

    private fun errorInvalidQuantity() {
        state.errorCode = 3
        state.errorMessage = "Invalid quantity format."
    }
}
