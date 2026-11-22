package br.com.wdc.shopping.presentation.presenter.restricted.receipt

import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.exception.WrongParametersException
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.structs.ReceiptForm

class ReceiptService(private val repo: PurchaseRepository) {

    constructor(app: RootPresenter) : this(app.getPurchaseRepository())

    /**
     * The receipt of a purchase **of the given user**.
     *
     * The purchase id arrives through navigation — the user is the one who chooses it —, so the lookup is always
     * restricted to whoever is logged in: asking for someone else's purchase is the same as asking for one that does
     * not exist.
     */
    suspend fun loadReceipt(purchaseId: Long?, userId: Long?): ReceiptForm? {
        if (purchaseId == null || userId == null) throw WrongParametersException()

        val criteria = PurchaseCriteria()
            .withPurchaseId(purchaseId)
            .withUserId(userId)
            .withProjection(ReceiptForm.projection())
        val receipt = ReceiptForm.create(repo.fetch(criteria, limit = 1).firstOrNull())
        receipt?.items?.sortBy { it.id }
        return receipt
    }
}
