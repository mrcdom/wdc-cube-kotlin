package br.com.wdc.shopping.presentation.presenter.restricted.cart

import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.function.Registration
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.ScopeSlot
import br.com.wdc.framework.cube.FormParam
import br.com.wdc.shopping.domain.exception.InvalidCartItemException
import br.com.wdc.shopping.domain.exception.OfflineException
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeKeys
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptKeys
import kotlin.time.Duration.Companion.seconds

class CartPresenter(app: RootPresenter) : AbstractCubePresenter<RootPresenter>(app) {

    companion object {
        private val LOG = Log.getLogger("CartPresenter")
    }

    // :: Public Instance Fields

    val scope = bind(CartScope())

    // :: Internal Instance Fields

    private val cart: CartManager = app.cart!!
    private var ownerSlot: ScopeSlot? = null
    private var pendingErrorClear: Registration? = null

    /** The rows alive, by product: a row that stays is reused, so only what changed in it repaints. */
    private val rows = LinkedHashMap<Long, CartItemScope>()

    init {
        scope.onBuy = action(1) { buy() }
        scope.onOpenProducts = action(3) { HomeKeys(app).flip() }
    }

    // :: Cube API

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        syncRows()

        // read on every call: a reused presenter goes where its parent puts it now, not where it was first put
        ownerSlot = CartKeys(app, intent).parentSlot

        ownerSlot?.setScope(scope)

        return true
    }

    // :: Rows

    /** Makes the rows follow the cart: reuses the ones that stay, creates the new ones, releases the ones that left. */
    private fun syncRows() {
        val items = cart.getCartItems()
        val next = items.map { item ->
            rows.getOrPut(item.id) { newRow(item.id) }.apply {
                name = item.name
                image = item.image
                price = item.price
                quantity = item.quantity
            }
        }
        val alive = items.mapTo(HashSet()) { it.id }
        for (productId in rows.keys.filter { it !in alive }) {
            detach(rows.remove(productId)!!)
        }
        scope.rows = next
        scope.total = items.sumOf { it.price * it.quantity }
    }

    private fun newRow(productId: Long): CartItemScope = segment(CartItemScope()).also { row ->
        row.productId = productId
        row.onRemove = action(1, target = row) { removeProduct(productId) }
        row.onModifyQuantity = actionOf(2, FormParam.int("p.quantity"), target = row) { modifyQuantity(productId, it) }
    }

    // :: User Actions

    private fun modifyQuantity(productId: Long?, quantity: Int?) {
        if (productId == null) {
            errorMalformedProductId()
            LOG.warn("onModifyQuantity.errorMalformedProductId: {}", scope.errorMessage)
            return
        }

        if (quantity == null) {
            errorMalformedQuantity()
            LOG.warn("onModifyQuantity.errorMalformedQuantity: {}", scope.errorMessage)
            return
        }

        if (quantity < 1) {
            alertThereIsItemWhichValueIsLessThanOne()
            LOG.warn("onModifyQuantity.alertThereIsItemWhichValueIsLessThanOne: {}", scope.errorMessage)
            return
        }

        val found = cart.modifyProductQuantity(productId, quantity)
        if (!found) {
            alertProductNotFound()
            LOG.warn("onModifyQuantity.alertProductNotFound: {}", scope.errorMessage)
        } else {
            syncRows()
        }
    }

    private suspend fun removeProduct(productId: Long?) {
        if (productId == null) {
            errorMalformedProductId()
            LOG.warn("onRemoveProduct: {}", scope.errorMessage)
            return
        }

        val modified = cart.removeProduct(productId)
        if (modified) {
            if (cart.getSize() == 0) {
                HomeKeys(app).flip()
            } else {
                syncRows()
            }
        }
    }

    private suspend fun buy() {
        if (cart.getSize() == 0) {
            alertPurchaseOfEmptyCart()
            LOG.warn("onBuy: {}", scope.errorMessage)
            return
        }

        try {
            val purchaseId = cart.commit(app.subject!!)

            ReceiptKeys(app).apply {
                this.purchaseId = purchaseId
                purchaseMade = true
            }.flip()
        } catch (caught: InvalidCartItemException) {
            alertThereIsItemWhichValueIsLessThanOne()
            LOG.error("onBuy.alertThereIsItemWhichValueIsLessThanOne: {}", scope.errorMessage, caught)
        } catch (caught: OfflineException) {
            alertDatabaseOffline()
            LOG.error("onBuy.alertDatabaseOffline: {}", scope.errorMessage, caught)
        }
    }

    // :: Message Methods

    private fun clearErrorAfterDelay() {
        pendingErrorClear?.remove()
        pendingErrorClear = ScheduledExecutor.BEAN.get().schedule({
            scope.errorCode = 0
            scope.errorMessage = null
        }, 3.seconds)
    }

    private fun alertThereIsItemWhichValueIsLessThanOne() {
        scope.errorCode = 1
        scope.errorMessage = "Some products have a quantity of less than one item. Unable to purchase."
        clearErrorAfterDelay()
    }

    private fun alertProductNotFound() {
        scope.errorCode = 2
        scope.errorMessage = "Product not found in the database."
        clearErrorAfterDelay()
    }

    private fun alertPurchaseOfEmptyCart() {
        scope.errorCode = 3
        scope.errorMessage = "There must be at least one item in the cart to complete a purchase."
        clearErrorAfterDelay()
    }

    private fun alertDatabaseOffline() {
        scope.errorCode = 4
        scope.errorMessage = "The database is currently offline. Please wait a moment and try again."
        clearErrorAfterDelay()
    }

    private fun errorMalformedProductId() {
        scope.errorCode = 5
        scope.errorMessage = "Malformed product code."
        clearErrorAfterDelay()
    }

    private fun errorMalformedQuantity() {
        scope.errorCode = 6
        scope.errorMessage = "Malformed quantity value."
        clearErrorAfterDelay()
    }
}
