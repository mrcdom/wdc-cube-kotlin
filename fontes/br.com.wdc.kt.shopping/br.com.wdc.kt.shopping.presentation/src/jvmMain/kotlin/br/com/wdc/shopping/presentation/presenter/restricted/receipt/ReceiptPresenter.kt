package br.com.wdc.shopping.presentation.presenter.restricted.receipt

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.CubeViewSlot
import br.com.wdc.shopping.presentation.PlaceAttributes
import br.com.wdc.shopping.presentation.PlaceParameters
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.exception.PurchaseNotFoundException
import br.com.wdc.shopping.presentation.presenter.Routes
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.structs.ReceiptForm

class ReceiptPresenter(app: ShoppingApplication) : AbstractCubePresenter<ShoppingApplication>(app) {

    companion object {
        private val LOG = Log.getLogger("ReceiptPresenter")

        @JvmField
        var createView: ((ReceiptPresenter) -> CubeView)? = null
    }

    // :: Public Instance Fields

    val state = ReceiptViewState()

    // :: Internal Instance Fields

    private val receiptService = ReceiptService(app)
    private var purchaseId: Long? = null
    private var ownerSlot: CubeViewSlot? = null

    // :: Cube API

    override fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        state.notifySuccess = intent.getAttribute(PlaceAttributes.ATTR_PURCHASE_MADE) == true

        val pPurchaseId = intent.getParameterAsLong(PlaceParameters.PURCHASE_ID, purchaseId)
            ?: throw AssertionError("Missing PURCHASE_ID")

        if (state.receipt == null || pPurchaseId != purchaseId) {
            val receipt = loadReceipt(pPurchaseId)
            purchaseId = pPurchaseId
            state.receipt = receipt
            update()
        }

        if (initialization) {
            ownerSlot = intent.getViewSlot(PlaceAttributes.SLOT_OWNER)

            if (state.receipt == null) {
                throw AssertionError("Missing receipt")
            }

            view = createView?.invoke(this)
            update()
        }

        ownerSlot?.setView(view!!)

        return true
    }

    override fun publishParameters(intent: CubeIntent) {
        purchaseId?.let {
            intent.setParameter(PlaceParameters.PURCHASE_ID, it)
        }
    }

    // :: User Actions

    fun onPrint() {
        // Not implemented
    }

    fun onOpenProducts() {
        try {
            Routes.home(app)
        } catch (caught: Exception) {
            app.alertUnexpectedError(LOG, "Going to restricted home place", caught)
        }
    }

    // :: Data Loaders

    private fun loadReceipt(purchaseId: Long): ReceiptForm {
        return receiptService.loadReceipt(purchaseId) ?: throw PurchaseNotFoundException()
    }
}
