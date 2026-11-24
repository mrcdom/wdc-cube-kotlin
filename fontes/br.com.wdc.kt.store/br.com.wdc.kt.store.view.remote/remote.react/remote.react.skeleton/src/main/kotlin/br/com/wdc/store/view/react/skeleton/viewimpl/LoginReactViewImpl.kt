package br.com.wdc.store.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.store.presentation.presenter.open.login.LoginScope
import br.com.wdc.store.view.react.skeleton.util.ScopeViewImpl

/** The login: the password arrives ciphered with the session's key, which only this layer knows. */
class LoginReactViewImpl(private val session: ReactRootPresenter, scope: LoginScope) :
    ScopeViewImpl<LoginScope>(VID, session, scope) {

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
            scope.password = session.getDataSecurity().b64Decipher(CoerceUtils.asString(formData[fn]) ?: "")
        }
    }
}
