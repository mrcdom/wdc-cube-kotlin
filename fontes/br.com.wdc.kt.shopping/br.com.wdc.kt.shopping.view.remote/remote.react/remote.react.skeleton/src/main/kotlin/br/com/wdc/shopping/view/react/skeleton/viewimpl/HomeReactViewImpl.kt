package br.com.wdc.shopping.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import br.com.wdc.shopping.view.react.skeleton.util.GenericViewImpl

class HomeReactViewImpl(presenter: HomePresenter) : GenericViewImpl<HomePresenter>("473dbdd7a36a", presenter) {

    override suspend fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>) {
        presenter.scope.dispatch(eventCode, formData)
    }

    override fun writeScope(json: ExtensibleObjectOutput) {
        presenter.scope.write(instanceId, json)
    }
}
