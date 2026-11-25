package br.com.wdc.store.presentation.presenter.restricted.receipt

import br.com.wdc.framework.cube.Scope
import br.com.wdc.store.presentation.presenter.restricted.receipt.structs.ReceiptForm
import br.com.wdc.framework.cube.Action

class ReceiptScope : Scope() {

    companion object {
        /** The client component that paints this scope in a remote view: its id in the bridge protocol. */
        const val VID = "e8d0bd8ae3bc"
    }

    var notifySuccess: Boolean by field(false)
    var receipt: ReceiptForm? by field(null)

    var onOpenProducts: Action = Action.NOOP
}
