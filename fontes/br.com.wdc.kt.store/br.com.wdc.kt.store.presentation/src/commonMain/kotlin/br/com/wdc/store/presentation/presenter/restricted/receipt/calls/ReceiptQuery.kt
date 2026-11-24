package br.com.wdc.store.presentation.presenter.restricted.receipt.calls

import br.com.wdc.framework.domain.resource.ResourceQuery
import br.com.wdc.store.domain.purchase.PurchaseCriteria
import br.com.wdc.store.domain.purchase.PurchaseRepository
import br.com.wdc.store.presentation.presenter.restricted.receipt.structs.ReceiptForm

/**
 * The receipt of a purchase **of the given buyer**, or `null`.
 *
 * The purchase id arrives through navigation — the user is the one who chooses it —, so the lookup is always
 * restricted to whoever is logged in: asking for someone else's purchase is the same as asking for one that does not
 * exist.
 */
class ReceiptQuery(private val purchaseId: Long, private val userId: Long) : ResourceQuery<ReceiptForm?>() {

    private val purchaseRepo: PurchaseRepository get() = PurchaseRepository.BEAN.get()

    override suspend fun run(): ReceiptForm? {
        val criteria = PurchaseCriteria()
            .withPurchaseId(purchaseId)
            .withUserId(userId)
            .withProjection(ReceiptForm.projection())
        val receipt = ReceiptForm.create(purchaseRepo.fetch(criteria, limit = 1).firstOrNull())
        receipt?.items?.sortBy { it.id }
        return receipt
    }
}
