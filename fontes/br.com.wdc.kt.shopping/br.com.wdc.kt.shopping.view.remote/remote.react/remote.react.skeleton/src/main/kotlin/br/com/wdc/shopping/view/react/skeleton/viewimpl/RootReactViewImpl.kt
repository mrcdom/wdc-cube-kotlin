package br.com.wdc.shopping.view.react.skeleton.viewimpl

import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.shopping.view.react.skeleton.util.GenericViewImpl

class RootReactViewImpl(presenter: RootPresenter) : GenericViewImpl<RootPresenter>("f2d345c4a610", presenter) {

    override suspend fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>) {
        // NOOP
    }

    override fun writeScope(json: ExtensibleObjectOutput) {
        val scope = presenter.scope

        json.beginObject()
        run {
            json.name("id").value(instanceId)

            val contentView = scope.contentView
            if (contentView is GenericViewImpl<*>) {
                json.name("contentViewId").value(contentView.instanceId)
            }

            if (!scope.errorMessage.isNullOrBlank()) {
                json.name("errorMessage").value(scope.errorMessage)
            }
        }
        json.endObject()
    }
}
