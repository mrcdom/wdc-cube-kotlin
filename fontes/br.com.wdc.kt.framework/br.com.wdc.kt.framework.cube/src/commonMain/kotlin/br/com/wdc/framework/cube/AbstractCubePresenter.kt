package br.com.wdc.framework.cube

open class AbstractCubePresenter<A : CubeApplication>(
    val app: A,
) : CubePresenter {

    protected var view: CubeView? = null

    fun view(): CubeView? = view

    override fun release() {
        view?.release()
        view = null
    }

    fun update() {
        view?.update()
    }

    override fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean = true

    override fun publishParameters(intent: CubeIntent) {
        // NOOP
    }
}
