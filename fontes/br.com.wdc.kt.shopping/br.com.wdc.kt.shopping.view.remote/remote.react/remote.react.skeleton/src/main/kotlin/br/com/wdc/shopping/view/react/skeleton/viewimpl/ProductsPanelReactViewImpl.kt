package br.com.wdc.shopping.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelPresenter
import br.com.wdc.shopping.view.react.skeleton.util.GenericViewImpl

class ProductsPanelReactViewImpl(presenter: ProductsPanelPresenter) : GenericViewImpl<ProductsPanelPresenter>("a1b2c3d4e5f6", presenter) {

    override suspend fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>) {
        presenter.scope.dispatch(eventCode, formData)
    }

    override fun writeScope(json: ExtensibleObjectOutput) {
        presenter.scope.write(instanceId, json)
    }
}
