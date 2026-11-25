package br.com.wdc.store.presentation.presenter.open.login

import br.com.wdc.framework.cube.Scope
import br.com.wdc.framework.cube.Action

class LoginScope : Scope() {

    var userName: String? by field(null)
    var password: String? by field(null, sent = false)
    var errorCode: Int by field(0)
    var errorMessage: String? by field(null)
    var loading: Boolean by field(false)

    /**
     * Signs in with the user name and password in this scope: the view puts what the user typed there first. A remote
     * view does it by sync, with the password ciphered with the session's key, which only the remote layer deciphers;
     * the password never goes back to a view (`sent = false`).
     */
    var onEnter: Action = Action.NOOP
}
