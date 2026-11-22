package br.com.wdc.shopping.presentation.presenter.restricted.receipt

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.CubeViewSlot
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.exception.PurchaseNotFoundException
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeKeys
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.structs.ReceiptForm

class ReceiptPresenter(app: ShoppingApplication) : AbstractCubePresenter<ShoppingApplication>(app) {

    companion object {
        private val LOG = Log.getLogger("ReceiptPresenter")

        var createView: ((ReceiptPresenter) -> CubeView)? = null
    }

    // :: Public Instance Fields

    val state = observed(ReceiptViewState())

    // :: Internal Instance Fields

    private val receiptService = ReceiptService(app)
    private var purchaseId: Long? = null
    private var ownerSlot: CubeViewSlot? = null

    // :: Cube API

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        val keys = ReceiptKeys(app, intent)
        val pPurchaseId = keys.purchaseId ?: purchaseId ?: run {
            // no usable id in the address: nothing to show; go to the home, with the intent in transit
            HomeKeys(app, intent).go()
            return false
        }
        state.notifySuccess = keys.purchaseMade

        if (state.receipt == null || pPurchaseId != purchaseId) {
            val receipt = loadReceipt(pPurchaseId)
            purchaseId = pPurchaseId
            state.receipt = receipt
        }

        // read on every call: a reused presenter goes where its parent puts it now, not where it was first put
        ownerSlot = keys.parentSlot
        if (initialization) {
            if (state.receipt == null) {
                throw AssertionError("Missing receipt")
            }

            view = createView?.invoke(this)
        }

        ownerSlot?.setView(view!!)

        return true
    }

    override fun publishParameters(intent: CubeIntent) {
        ReceiptKeys(app, intent).purchaseId = purchaseId
    }

    // :: User Actions

    fun onPrint() {
        // Not implemented
    }

    suspend fun onOpenProducts() {
        try {
            HomeKeys(app).go()
        } catch (caught: Exception) {
            app.alertUnexpectedError(LOG, "Going to restricted home place", caught)
        }
    }

    // :: Data Loaders

    private suspend fun loadReceipt(purchaseId: Long): ReceiptForm {
        return receiptService.loadReceipt(purchaseId, app.subject?.id) ?: throw PurchaseNotFoundException()
    }
}
