package br.com.wdc.shopping.presentation.presenter

import br.com.wdc.framework.cube.Scope

class RootScope : Scope() {

    /** The scope of the first level (login or home). */
    var content: Scope? by field(null)
    var errorMessage: String? by field(null)
}
