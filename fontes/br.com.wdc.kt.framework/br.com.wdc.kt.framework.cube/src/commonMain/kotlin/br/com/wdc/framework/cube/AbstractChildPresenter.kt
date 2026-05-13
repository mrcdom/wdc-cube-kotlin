package br.com.wdc.framework.cube

abstract class AbstractChildPresenter<A : CubeApplication>(
    val app: A,
) {

    var view: CubeView? = null
        protected set

    fun initialize(): CubeView {
        val v = onCreateView()
        view = v
        onInitialize()
        return v
    }

    open fun release() {
        view?.release()
        view = null
    }

    fun update() {
        view?.update()
    }

    protected abstract fun onCreateView(): CubeView

    protected abstract fun onInitialize()
}
