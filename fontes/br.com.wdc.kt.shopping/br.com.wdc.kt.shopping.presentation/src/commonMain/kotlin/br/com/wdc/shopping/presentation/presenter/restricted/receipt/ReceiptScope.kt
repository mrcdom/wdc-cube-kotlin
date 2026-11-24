package br.com.wdc.shopping.presentation.presenter.restricted.receipt

import br.com.wdc.framework.cube.Scope
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.structs.ReceiptForm

class ReceiptScope : Scope() {

    var notifySuccess: Boolean by field(false)
    var receipt: ReceiptForm? by field(null)
}
