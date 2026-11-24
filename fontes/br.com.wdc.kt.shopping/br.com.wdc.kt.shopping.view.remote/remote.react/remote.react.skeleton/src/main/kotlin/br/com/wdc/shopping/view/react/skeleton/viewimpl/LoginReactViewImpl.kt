package br.com.wdc.shopping.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.shopping.presentation.presenter.open.login.LoginScope
import br.com.wdc.shopping.view.react.skeleton.util.ScopeViewImpl

/** The login: the password arrives ciphered with the session's key, which only this layer knows. */
class LoginReactViewImpl(app: ReactRootPresenter, scope: LoginScope) : ScopeViewImpl<LoginScope>(VID, app, scope) {

    companion object {
        const val VID = "c677cda52d14"
    }

    override fun syncClientToServer(formData: Map<String, Any?>) {
        var fn = "userName"
        if (formData.containsKey(fn)) {
            scope.userName = CoerceUtils.asString(formData[fn])
        }

        fn = "password"
        if (formData.containsKey(fn)) {
            scope.password = app.getDataSecurity().b64Decipher(CoerceUtils.asString(formData[fn]) ?: "")
        }
    }
}
