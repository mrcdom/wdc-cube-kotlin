package br.com.wdc.framework.cube

open class AbstractCubePresenter<A : CubeApplication>(app: A) : AbstractPresenter<A>(app), CubePresenter {

    protected var view: CubeView? = null

    fun view(): CubeView? = view

    override fun release() {
        view?.release()
        view = null
    }

    override fun update() {
        view?.update()
    }

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean = true

}
