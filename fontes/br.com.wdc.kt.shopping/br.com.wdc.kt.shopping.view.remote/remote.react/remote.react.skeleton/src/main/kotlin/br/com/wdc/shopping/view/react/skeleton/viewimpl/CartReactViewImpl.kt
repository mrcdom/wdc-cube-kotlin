package br.com.wdc.shopping.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartPresenter
import br.com.wdc.shopping.view.react.skeleton.util.GenericViewImpl

class CartReactViewImpl(presenter: CartPresenter) : GenericViewImpl<CartPresenter>("7eb485e5f843", presenter) {

    @Throws(Exception::class)
    override suspend fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>) {
        when (eventCode) {
            1 -> presenter.onBuy()
            2 -> presenter.onRemoveProduct(CoerceUtils.asLong(formData["p.productId"]) ?: 0L)
            3 -> presenter.onOpenProducts()
        }
    }

    override fun writeState(json: ExtensibleObjectOutput) {
        presenter.state.write(instanceId, json)
    }
}
