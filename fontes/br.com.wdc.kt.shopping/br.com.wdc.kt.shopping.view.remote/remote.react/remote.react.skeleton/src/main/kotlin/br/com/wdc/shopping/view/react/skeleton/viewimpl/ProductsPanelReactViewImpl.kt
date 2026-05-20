package br.com.wdc.shopping.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelPresenter
import br.com.wdc.shopping.view.react.skeleton.util.GenericViewImpl

class ProductsPanelReactViewImpl(private val presenter: ProductsPanelPresenter) : GenericViewImpl(presenter.app, "a1b2c3d4e5f6") {

    @Throws(Exception::class)
    override fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>) {
        when (eventCode) {
            1 -> presenter.onOpenProduct(CoerceUtils.asLong(formData["p.productId"]) ?: 0L)
        }
    }

    override fun writeState(json: ExtensibleObjectOutput) {
        presenter.state.write(instanceId, json)
    }
}
