package br.com.wdc.shopping.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelPresenter
import br.com.wdc.shopping.view.react.skeleton.util.GenericViewImpl

class PurchasesPanelReactViewImpl(private val presenter: PurchasesPanelPresenter) : GenericViewImpl(presenter.app, "b3c4d5e6f7a8") {

    @Throws(Exception::class)
    override fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>) {
        when (eventCode) {
            1 -> presenter.onOpenReceipt(CoerceUtils.asLong(formData["p.purchaseId"]) ?: 0L)
            2 -> presenter.onPageChange(CoerceUtils.asInteger(formData["p.page"]) ?: 0)
            3 -> presenter.onItemSizeCapacityChanged(CoerceUtils.asInteger(formData["p.capacity"]) ?: 0)
        }
    }

    override fun writeState(json: ExtensibleObjectOutput) {
        presenter.state.write(instanceId, json)
    }
}
