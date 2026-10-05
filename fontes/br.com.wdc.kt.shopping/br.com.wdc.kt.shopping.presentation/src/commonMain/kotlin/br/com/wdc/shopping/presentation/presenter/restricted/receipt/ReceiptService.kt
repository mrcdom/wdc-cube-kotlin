package br.com.wdc.shopping.presentation.presenter.restricted.receipt

import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.exception.WrongParametersException
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.structs.ReceiptForm

class ReceiptService(private val repo: PurchaseRepository) {

    constructor(app: ShoppingApplication) : this(app.getPurchaseRepository())

    /**
     * O recibo de uma compra **do usuário informado**.
     *
     * O id da compra chega pela navegação — é o usuário quem o escolhe —, e por isso a busca é sempre
     * restrita a quem está logado: pedir a compra de outro é o mesmo que pedir uma que não existe.
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
