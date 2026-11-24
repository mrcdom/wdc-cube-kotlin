package br.com.wdc.store.presentation.presenter.restricted.products

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.ScopeSlot
import br.com.wdc.store.domain.exception.OfflineException
import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.restricted.cart.CartKeys
import br.com.wdc.store.presentation.presenter.restricted.home.HomeKeys
import br.com.wdc.framework.cube.FormParam
import br.com.wdc.store.presentation.presenter.restricted.products.calls.ProductQuery

class ProductPresenter(app: RootPresenter) : AbstractCubePresenter<RootPresenter>(app) {

    companion object {
        private val LOG = Log.getLogger("ProductPresenter")
    }

    // :: Public Instance Fields

    val scope = bind(ProductScope())

    // :: Internal Instance Fields

    private var ownerSlot: ScopeSlot? = null

    init {
        scope.onOpenProducts = action(1) { HomeKeys(app).flip() }
        scope.onAddToCart = actionOf(2, FormParam.int("p.quantity")) { addToCart(it) }
    }

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
            scope.product = ProductQuery(productId = newProductId).run()
        }

        // read on every call: a reused presenter goes where its parent puts it now, not where it was first put
        ownerSlot = keys.parentSlot
        if (initialization) {
            scope.errorCode = 0
            scope.errorMessage = null
            if (scope.product == null) {
                throw AssertionError("Missing Product")
            }

        }

        ownerSlot?.setScope(scope)

        return true
    }

    override fun publishParameters(intent: CubeIntent) {
        ProductKeys(app, intent).productId = scope.product?.id
    }

    // :: User Actions

    private suspend fun addToCart(quantity: Int?) {
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

        try {
            app.cart!!.addProduct(scope.product!!, quantity)

            CartKeys(app).flip()
        } catch (caught: OfflineException) {
            alertDatabaseNotAvailable()
            LOG.error(scope.errorMessage!!, caught)
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
