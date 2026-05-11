package br.com.wdc.shopping.presentation.presenter.restricted.products

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.CubeViewSlot
import br.com.wdc.shopping.domain.exception.OfflineException
import br.com.wdc.shopping.presentation.PlaceAttributes
import br.com.wdc.shopping.presentation.PlaceParameters
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.Routes

class ProductPresenter(app: ShoppingApplication) : AbstractCubePresenter<ShoppingApplication>(app) {

    companion object {
        private val LOG = Log.getLogger(ProductPresenter::class.java)

        @JvmField
        var createView: ((ProductPresenter) -> CubeView)? = null
    }

    // :: Public Instance Fields

    val state = ProductViewState()

    // :: Internal Instance Fields

    private val productService = ProductService(app)
    private var ownerSlot: CubeViewSlot? = null

    // :: Cube API

    override fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        val oldProductId = state.product?.id

        val newProductId = intent.getParameterAsLong(PlaceParameters.PRODUCT_ID, oldProductId)
            ?: throw AssertionError("Missing PRODUCT_ID")

        if (state.product == null || newProductId != oldProductId) {
            state.product = productService.loadProductById(newProductId)
            update()
        }

        if (initialization) {
            ownerSlot = intent.getViewSlot(PlaceAttributes.SLOT_OWNER)

            state.errorCode = 0
            state.errorMessage = null
            if (state.product == null) {
                throw AssertionError("Missing Product")
            }

            view = createView?.invoke(this)
            update()
        }

        ownerSlot?.setView(view!!)

        return true
    }

    override fun publishParameters(intent: CubeIntent) {
        state.product?.let {
            intent.setParameter(PlaceParameters.PRODUCT_ID, it.id)
        }
    }

    // :: User Actions

    fun onAddToCart(quantity: Int?) {
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

    fun onOpenProducts() {
        try {
            Routes.home(app)
        } catch (caught: Exception) {
            app.alertUnexpectedError(LOG, "Going to home of restricted place", caught)
        }
    }

    // :: Messages

    private fun alertDatabaseNotAvailable() {
        state.errorCode = 1
        state.errorMessage = "Carrinho não acessível. Aguarde alguns instantes e tente novamente."
        update()
    }

    private fun alertCartItemWidthLessThanOneItem() {
        state.errorCode = 2
        state.errorMessage = "A quantidade de itens no carrinho deve ser maior ou igual a 1 (um)."
        update()
    }

    private fun errorInvalidQuantity() {
        state.errorCode = 3
        state.errorMessage = "Codificação errada da quantidade."
        update()
    }
}
