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
import java.time.Duration

class CartPresenter(app: ShoppingApplication) : AbstractCubePresenter<ShoppingApplication>(app) {

    companion object {
        private val LOG = Log.getLogger(CartPresenter::class.java)

        @JvmField
        var createView: ((CartPresenter) -> CubeView)? = null
    }

    // :: Public Instance Fields

    val state = CartViewState()

    // :: Internal Instance Fields

    private val cart: CartManager = app.cart!!
    private var ownerSlot: CubeViewSlot? = null
    private var pendingErrorClear: Registration? = null

    init {
        state.items = emptyList()
    }

    // :: Cube API

    override fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        state.items = cart.getCartItems()

        if (initialization) {
            ownerSlot = intent.getViewSlot(PlaceAttributes.SLOT_OWNER)
            view = createView?.invoke(this)
        }

        ownerSlot?.setView(view!!)

        return true
    }

    // :: User Actions

    fun onModifyQuantity(productId: Long?, quantity: Int?) {
        try {
            if (productId == null) {
                errorCodigoDeProdutoMalFormatado()
                LOG.warn("onModifyQuantity.errorCodigoDeProdutoMalFormatado: {}", state.errorMessage)
                return
            }

            if (quantity == null) {
                errorValorQuantidadeMalFormatado()
                LOG.warn("onModifyQuantity.errorValorQuantidadeMalFormatado: {}", state.errorMessage)
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
                update()
            }
        } catch (caught: Exception) {
            app.alertUnexpectedError(LOG, "Removing a prouduct", caught)
        }
    }

    fun onRemoveProduct(productId: Long?) {
        try {
            if (productId == null) {
                errorCodigoDeProdutoMalFormatado()
                LOG.warn("onRemoveProduct: {}", state.errorMessage)
                return
            }

            val modified = cart.removeProduct(productId)
            if (modified) {
                if (cart.getSize() == 0) {
                    Routes.home(app)
                } else {
                    state.items = cart.getCartItems()
                    update()
                }
            }
        } catch (caught: Exception) {
            app.alertUnexpectedError(LOG, "Removing a prouduct", caught)
        }
    }

    fun onBuy() {
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

            app.alertUnexpectedError(LOG, "Buying an product", caught)
        }
    }

    fun onOpenProducts() {
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
            update()
        }, Duration.ofSeconds(3))
    }

    private fun alertThereIsItemWhichValueIsLessThanOne() {
        state.errorCode = 1
        state.errorMessage = "Deve existir pelo menos um item no carrinhro para se efetivar uma compra"
        update()
        clearErrorAfterDelay()
    }

    private fun alertProductNotFound() {
        state.errorCode = 2
        state.errorMessage = "Produdo não localizado na base dados."
        update()
        clearErrorAfterDelay()
    }

    private fun alertPurchaseOfEmptyCart() {
        state.errorCode = 3
        state.errorMessage = "Existem produtos com menos de um item na quantidade. Impossível comprar."
        update()
        clearErrorAfterDelay()
    }

    private fun alertDatabaseOffline() {
        state.errorCode = 4
        state.errorMessage = "O banco de dados encontra-se fora do ar no momento. Aguarde alguns instantes e tente novamente."
        update()
        clearErrorAfterDelay()
    }

    private fun errorCodigoDeProdutoMalFormatado() {
        state.errorCode = 5
        state.errorMessage = "Código do produto mal formado."
        update()
        clearErrorAfterDelay()
    }

    private fun errorValorQuantidadeMalFormatado() {
        state.errorCode = 6
        state.errorMessage = "Valor da quantiade está mal formado."
        update()
        clearErrorAfterDelay()
    }
}
