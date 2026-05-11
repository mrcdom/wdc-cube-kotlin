package br.com.wdc.shopping.presentation.presenter

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.ViewState

class RootViewState : ViewState {

    var contentView: CubeView? = null
    var errorMessage: String? = null

    override fun write(instanceId: String, json: ExtensibleObjectOutput) {
        json.beginObject()

        json.name("id").value(instanceId)

        contentView?.let {
            json.name("contentViewId").value(it.instanceId())
        }

        if (!errorMessage.isNullOrBlank()) {
            json.name("errorMessage").value(errorMessage)
        }

        json.endObject()
    }
}
