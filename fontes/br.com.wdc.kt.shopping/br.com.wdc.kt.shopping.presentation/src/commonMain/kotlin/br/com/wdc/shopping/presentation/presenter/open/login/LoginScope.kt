package br.com.wdc.shopping.presentation.presenter.open.login

import br.com.wdc.framework.cube.Scope
import br.com.wdc.framework.cube.ActionOf2

class LoginScope : Scope() {

    var userName: String? by field(null)
    var password: String? by field(null, sent = false)
    var errorCode: Int by field(0)
    var errorMessage: String? by field(null)
    var loading: Boolean by field(false)

    /** Signs in with the given user name and password; `null` keeps what the view already put in the scope. */
    var onEnter: ActionOf2<String?, String?> = ActionOf2.noop()
}
