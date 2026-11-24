package br.com.wdc.shopping.presentation.presenter.open.login

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.Scope

class LoginScope : Scope() {

    var userName: String? by field(null)
    var password: String? by field(null)
    var errorCode: Int by field(0)
    var errorMessage: String? by field(null)
    var loading: Boolean by field(false)

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
