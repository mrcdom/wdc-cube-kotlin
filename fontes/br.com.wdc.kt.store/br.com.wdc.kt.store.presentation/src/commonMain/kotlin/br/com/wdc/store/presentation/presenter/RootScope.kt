package br.com.wdc.store.presentation.presenter

import br.com.wdc.framework.cube.Scope

class RootScope : Scope() {

    companion object {
        /** The client component that paints this scope in a remote view: its id in the bridge protocol. */
        const val VID = "f2d345c4a610"
    }

    /** The scope of the first level (login or home). */
    var content: Scope? by field(null)
    var errorMessage: String? by field(null)
}
