package br.com.wdc.shopping.presentation.presenter.restricted.receipt

import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.AbstractCubePresenter
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.ScopeSlot
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.exception.PurchaseNotFoundException
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeKeys
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.structs.ReceiptForm

class ReceiptPresenter(app: RootPresenter) : AbstractCubePresenter<RootPresenter>(app) {

    companion object {
        private val LOG = Log.getLogger("ReceiptPresenter")
    }

    // :: Public Instance Fields

    val scope = bind(ReceiptScope())

    // :: Internal Instance Fields

    private val receiptService = ReceiptService(PurchaseRepository.BEAN.get())
    private var purchaseId: Long? = null
    private var ownerSlot: ScopeSlot? = null

    init {
        scope.onOpenProducts = action(1) { HomeKeys(app).flip() }
    }

    // :: Cube API

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean {
        val keys = ReceiptKeys(app, intent)
        val pPurchaseId = keys.purchaseId ?: purchaseId ?: run {
            // no usable id in the address: nothing to show; go to the home, with the intent in transit
            HomeKeys(app, intent).flip()
            return false
        }
        scope.notifySuccess = keys.purchaseMade

        if (scope.receipt == null || pPurchaseId != purchaseId) {
            val receipt = loadReceipt(pPurchaseId)
            purchaseId = pPurchaseId
            scope.receipt = receipt
        }

        // read on every call: a reused presenter goes where its parent puts it now, not where it was first put
        ownerSlot = keys.parentSlot
        if (initialization) {
            if (scope.receipt == null) {
                throw AssertionError("Missing receipt")
            }

        }

        ownerSlot?.setScope(scope)

        return true
    }

    override fun publishParameters(intent: CubeIntent) {
        ReceiptKeys(app, intent).purchaseId = purchaseId
    }

    // :: Data Loaders

    private suspend fun loadReceipt(purchaseId: Long): ReceiptForm {
        return receiptService.loadReceipt(purchaseId, app.subject?.id) ?: throw PurchaseNotFoundException()
    }
}
