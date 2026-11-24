package br.com.wdc.shopping.presentation.presenter.restricted.home

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.Scope
import br.com.wdc.framework.cube.Action

class HomeScope : Scope() {

    var contentView: CubeView? by field(null)
    var productsPanelView: CubeView? by field(null)
    var purchasesPanelView: CubeView? by field(null)
    var nickName: String? by field(null)
    var cartItemCount: Int by field(0)
    var errorCode: Int by field(0)
    var errorMessage: String? by field(null)

    var onOpenCart: Action = Action.NOOP
    var onExit: Action = Action.NOOP
}
