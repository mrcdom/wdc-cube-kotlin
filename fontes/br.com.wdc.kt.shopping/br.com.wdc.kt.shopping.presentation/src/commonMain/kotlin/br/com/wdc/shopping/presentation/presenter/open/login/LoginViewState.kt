package br.com.wdc.shopping.presentation.presenter.open.login

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.ViewState

class LoginViewState : ViewState {

    var userName: String? = null
    var password: String? = null
    var errorCode: Int = 0
    var errorMessage: String? = null
    var loading: Boolean = false

    override fun write(instanceId: String, json: ExtensibleObjectOutput) {
        json.beginObject()

        json.name("id").value(instanceId)
        json.name("loading").value(loading)

        if (!userName.isNullOrBlank()) {
            json.name("userName").value(userName)
        }

        if (!errorMessage.isNullOrBlank()) {
            json.name("errorMessage").value(errorMessage)
        }

        json.endObject()
    }
}
