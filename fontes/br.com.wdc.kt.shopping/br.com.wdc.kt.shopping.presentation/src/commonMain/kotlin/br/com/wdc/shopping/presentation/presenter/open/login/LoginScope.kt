package br.com.wdc.shopping.presentation.presenter.open.login

import br.com.wdc.framework.cube.Scope

class LoginScope : Scope() {

    var userName: String? by field(null)
    var password: String? by field(null, sent = false)
    var errorCode: Int by field(0)
    var errorMessage: String? by field(null)
    var loading: Boolean by field(false)
}
