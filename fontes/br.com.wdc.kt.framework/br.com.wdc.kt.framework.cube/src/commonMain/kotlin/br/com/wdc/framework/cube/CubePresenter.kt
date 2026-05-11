package br.com.wdc.framework.cube

interface CubePresenter {

    fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean

    fun publishParameters(intent: CubeIntent)

    fun commitComputedState() {
        // NOOP
    }

    fun release()
}
