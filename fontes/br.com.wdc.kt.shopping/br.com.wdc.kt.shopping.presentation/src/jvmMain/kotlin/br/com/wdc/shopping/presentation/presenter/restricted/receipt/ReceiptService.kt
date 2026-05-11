package br.com.wdc.shopping.presentation.presenter.restricted.receipt

import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.exception.WrongParametersException
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.structs.ReceiptForm

class ReceiptService {

    private val repo: PurchaseRepository

    constructor(app: ShoppingApplication) {
        this.repo = app.getPurchaseRepository()
    }

    constructor(repo: PurchaseRepository) {
        this.repo = repo
    }

    fun loadReceipt(purchaseId: Long?): ReceiptForm? {
        if (purchaseId == null) throw WrongParametersException()

        val receipt = ReceiptForm.create(repo.fetchById(purchaseId, ReceiptForm.projection()))
        receipt?.items?.sortBy { it.id }
        return receipt
    }
}
