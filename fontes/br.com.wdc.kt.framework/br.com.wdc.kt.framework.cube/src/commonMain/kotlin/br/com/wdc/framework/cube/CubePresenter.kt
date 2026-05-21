package br.com.wdc.framework.cube

interface CubePresenter : PresenterBase {

    suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean

    fun publishParameters(intent: CubeIntent) {}

    override fun release()
}
