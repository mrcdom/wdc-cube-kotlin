package br.com.wdc.shopping.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.shopping.presentation.presenter.open.login.LoginPresenter
import br.com.wdc.shopping.view.react.skeleton.util.GenericViewImpl

class LoginReactViewImpl(private val presenter: LoginPresenter) : GenericViewImpl(presenter.app, "c677cda52d14") {

    override fun syncClientToServer(formData: Map<String, Any?>) {
        val state = presenter.state

        var fn = "userName"
        if (formData.containsKey(fn)) {
            state.userName = CoerceUtils.asString(formData[fn])
        }

        fn = "password"
        if (formData.containsKey(fn)) {
            state.password = app.getDataSecurity().b64Decipher(CoerceUtils.asString(formData[fn]) ?: "")
        }
    }

    override fun submit(eventCode: Int, eventQtde: Int, formData: Map<String, Any?>) {
        if (eventCode == 1) {
            presenter.onEnter()
        }
    }

    override fun writeState(json: ExtensibleObjectOutput) {
        presenter.state.write(instanceId(), json)
    }
}
