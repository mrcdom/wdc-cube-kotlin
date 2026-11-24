package br.com.wdc.store.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.store.presentation.presenter.restricted.receipt.ReceiptScope
import br.com.wdc.store.view.react.skeleton.util.ScopeViewImpl

class ReceiptReactViewImpl(app: ReactRootPresenter, scope: ReceiptScope) : ScopeViewImpl<ReceiptScope>(VID, app, scope) {

    companion object {
        const val VID = "e8d0bd8ae3bc"
    }

    override fun writeScope(json: ExtensibleObjectOutput) {
        super.writeScope(json)
        // the success notice shows once: sent, it is consumed
        scope.notifySuccess = false
    }
}
