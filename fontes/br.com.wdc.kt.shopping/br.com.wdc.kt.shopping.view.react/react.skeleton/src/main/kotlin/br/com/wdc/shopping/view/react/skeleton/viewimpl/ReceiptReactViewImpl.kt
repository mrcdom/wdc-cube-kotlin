package br.com.wdc.shopping.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptPresenter
import br.com.wdc.shopping.view.react.skeleton.util.GenericViewImpl

class ReceiptReactViewImpl(private val presenter: ReceiptPresenter) : GenericViewImpl(presenter.app, "e8d0bd8ae3bc", presenter) {

    @Throws(Exception::class)
    override fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>) {
        if (eventCode == 1) {
            presenter.onOpenProducts()
        }
    }

    override fun writeState(json: ExtensibleObjectOutput) {
        presenter.state.write(instanceId, json)
    }
}
