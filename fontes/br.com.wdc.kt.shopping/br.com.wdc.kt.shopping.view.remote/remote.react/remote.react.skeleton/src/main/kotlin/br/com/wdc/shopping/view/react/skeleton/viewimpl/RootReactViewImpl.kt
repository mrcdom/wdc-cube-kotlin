package br.com.wdc.shopping.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.view.react.skeleton.util.GenericViewImpl

class RootReactViewImpl(presenter: RootPresenter) : GenericViewImpl<RootPresenter>("f2d345c4a610", presenter) {

    init {
        app.setRootPresenter(presenter)
    }

    override fun release() {
        app.setRootPresenter(null)
        super.release()
    }

    override suspend fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>) {
        // NOOP
    }

    override fun writeState(json: ExtensibleObjectOutput) {
        val state = presenter.state

        json.beginObject()
        run {
            json.name("id").value(instanceId)

            val contentView = state.contentView
            if (contentView is GenericViewImpl<*>) {
                json.name("contentViewId").value(contentView.instanceId)
            }

            if (!state.errorMessage.isNullOrBlank()) {
                json.name("errorMessage").value(state.errorMessage)
            }
        }
        json.endObject()
    }
}
