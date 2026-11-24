package br.com.wdc.shopping.presentation.presenter

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.Scope

class RootScope : Scope() {

    var contentView: CubeView? by field(null)
    var errorMessage: String? by field(null)
}
