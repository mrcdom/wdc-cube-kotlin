package br.com.wdc.shopping.presentation.presenter.restricted.home

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.ViewState

class HomeViewState : ViewState {

    var contentView: CubeView? = null
    var productsPanelView: CubeView? = null
    var purchasesPanelView: CubeView? = null
    var nickName: String? = null
    var cartItemCount: Int = 0
    var errorCode: Int = 0
    var errorMessage: String? = null

    override fun write(instanceId: String, json: ExtensibleObjectOutput) {
        json.beginObject()

        json.name("id").value(instanceId)

        if (!nickName.isNullOrBlank()) {
            json.name("nickName").value(nickName)
        }

        json.name("cartItemCount").value(cartItemCount.toLong())

        contentView?.let {
            json.name("contentViewId").value(it.instanceId())
        }

        productsPanelView?.let {
            json.name("productsPanelViewId").value(it.instanceId())
        }

        purchasesPanelView?.let {
            json.name("purchasesPanelViewId").value(it.instanceId())
        }

        if (!errorMessage.isNullOrBlank()) {
            json.name("errorMessage").value(errorMessage)
        }

        json.endObject()
    }
}
