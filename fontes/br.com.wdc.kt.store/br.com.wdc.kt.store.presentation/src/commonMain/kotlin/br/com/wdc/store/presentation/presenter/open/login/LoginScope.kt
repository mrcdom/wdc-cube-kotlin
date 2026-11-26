package br.com.wdc.store.presentation.presenter.open.login

import br.com.wdc.framework.cube.Action
import br.com.wdc.framework.cube.Scope
import br.com.wdc.store.presentation.i18n.Message

class LoginScope : Scope() {

    companion object {
        /** The client component that paints this scope in a remote view: its id in the bridge protocol. */
        const val VID = "c677cda52d14"
    }

    var userName: String? by field(null)
    var password: String? by field(null, sent = false)
    var errorCode: Int by field(0)
    var errorMessage: Message? by field(null)
    var loading: Boolean by field(false)

    /**
     * Signs in with the user name and password in this scope: the view puts what the user typed there first. A remote
     * view does it by sync, with the password ciphered with the session's key; the presenter deciphers it
     * ([br.com.wdc.store.presentation.presenter.RootPresenter.b64Decipher]). The password never goes back to a view
     * (`sent = false`).
     */
    var onEnter: Action = Action.NOOP
}
