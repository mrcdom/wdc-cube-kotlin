package br.com.wdc.shopping.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductPresenter
import br.com.wdc.shopping.view.react.skeleton.util.GenericViewImpl

class ProductReactViewImpl(private val presenter: ProductPresenter) : GenericViewImpl(presenter.app, "48b693f67410", presenter) {

    @Throws(Exception::class)
    override fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>) {
        when (eventCode) {
            1 -> presenter.onOpenProducts()
            2 -> presenter.onAddToCart(CoerceUtils.asInteger(formData["p.quantity"]) ?: 0)
        }
    }

    override fun writeState(json: ExtensibleObjectOutput) {
        presenter.state.write(instanceId, json)
    }
}
