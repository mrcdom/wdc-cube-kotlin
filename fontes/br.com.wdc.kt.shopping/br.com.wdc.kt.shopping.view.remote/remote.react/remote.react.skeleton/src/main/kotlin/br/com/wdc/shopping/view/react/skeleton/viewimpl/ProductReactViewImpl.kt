package br.com.wdc.shopping.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductPresenter
import br.com.wdc.shopping.view.react.skeleton.util.GenericViewImpl

class ProductReactViewImpl(presenter: ProductPresenter) : GenericViewImpl<ProductPresenter>("48b693f67410", presenter) {

    override suspend fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>) {
        presenter.scope.dispatch(eventCode, formData)
    }

    override fun writeScope(json: ExtensibleObjectOutput) {
        presenter.scope.write(instanceId, json)
    }
}
