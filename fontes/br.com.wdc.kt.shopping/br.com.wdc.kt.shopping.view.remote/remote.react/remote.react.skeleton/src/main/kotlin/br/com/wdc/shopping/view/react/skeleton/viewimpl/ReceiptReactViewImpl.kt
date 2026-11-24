package br.com.wdc.shopping.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptPresenter
import br.com.wdc.shopping.view.react.skeleton.util.GenericViewImpl

class ReceiptReactViewImpl(presenter: ReceiptPresenter) : GenericViewImpl<ReceiptPresenter>("e8d0bd8ae3bc", presenter) {

    override suspend fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>) {
        presenter.scope.dispatch(eventCode, formData)
    }

    override fun writeScope(json: ExtensibleObjectOutput) {
        presenter.scope.write(instanceId, json)
        // the success notice shows once: sent, it is consumed
        presenter.scope.notifySuccess = false
    }
}
