package br.com.wdc.shopping.presentation.presenter.restricted.products

import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.CubeViewSlot
import br.com.wdc.shopping.domain.exception.OfflineException
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartKeys
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeKeys

class ProductPresenter(app: RootPresenter) : AbstractCubePresenter<RootPresenter>(app) {

    companion object {
        private val LOG = Log.getLogger("ProductPresenter")

        var createView: ((ProductPresenter) -> CubeView)? = null
    }

    // :: Public Instance Fields

    val scope = bind(ProductScope())

    // :: Internal Instance Fields

    private val productService = ProductService(ProductRepository.BEAN.get())
    private var ownerSlot: CubeViewSlot? = null

    // :: Cube API

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        val keys = ProductKeys(app, intent)
        val oldProductId = scope.product?.id

        val newProductId = keys.productId ?: oldProductId ?: run {
            // no usable id in the address: nothing to show; go to the home, with the intent in transit
            HomeKeys(app, intent).flip()
            return false
        }

        if (scope.product == null || newProductId != oldProductId) {
            scope.product = productService.loadProductById(newProductId)
        }

        // read on every call: a reused presenter goes where its parent puts it now, not where it was first put
        ownerSlot = keys.parentSlot
        if (initialization) {
            scope.errorCode = 0
            scope.errorMessage = null
            if (scope.product == null) {
                throw AssertionError("Missing Product")
            }

            view = createView?.invoke(this)
        }

        ownerSlot?.setView(view!!)

        return true
    }

    override fun publishParameters(intent: CubeIntent) {
        ProductKeys(app, intent).productId = scope.product?.id
    }

    // :: User Actions

    suspend fun onAddToCart(quantity: Int?) {
        try {
            if (quantity == null) {
                errorInvalidQuantity()
                LOG.warn("onAddToCart.errorInvalidQuantity: {}", scope.errorMessage)
                return
            }

            if (quantity < 1) {
                alertCartItemWidthLessThanOneItem()
                LOG.warn("onAddToCart.alertCartItemWidthLessThanOneItem: {}", scope.errorMessage)
                return
            }

            app.cart!!.addProduct(scope.product!!, quantity)

            CartKeys(app).flip()
        } catch (caught: Exception) {
            if (caught is OfflineException) {
                alertDatabaseNotAvailable()
                LOG.error(scope.errorMessage!!, caught)
                return
            }

            app.alertUnexpectedError(LOG, "Adding an item to cart", caught)
        }
    }

    suspend fun onOpenProducts() {
        try {
            HomeKeys(app).flip()
        } catch (caught: Exception) {
            app.alertUnexpectedError(LOG, "Going to home of restricted place", caught)
        }
    }

    // :: Messages

    private fun alertDatabaseNotAvailable() {
        scope.errorCode = 1
        scope.errorMessage = "The cart is not available. Please wait a moment and try again."
    }

    private fun alertCartItemWidthLessThanOneItem() {
        scope.errorCode = 2
        scope.errorMessage = "The quantity of items in the cart must be at least 1 (one)."
    }

    private fun errorInvalidQuantity() {
        scope.errorCode = 3
        scope.errorMessage = "Invalid quantity format."
    }
}
