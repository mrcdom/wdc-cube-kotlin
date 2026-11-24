package br.com.wdc.shopping.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartPresenter
import br.com.wdc.shopping.view.react.skeleton.util.GenericViewImpl

class CartReactViewImpl(presenter: CartPresenter) : GenericViewImpl<CartPresenter>("7eb485e5f843", presenter) {

    override suspend fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>) {
        presenter.scope.dispatch(eventCode, formData)
    }

    override fun writeScope(json: ExtensibleObjectOutput) {
        presenter.scope.write(instanceId, json)
    }
}
