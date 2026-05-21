package br.com.wdc.framework.cube

abstract class AbstractChildPresenter<A : CubeApplication>(
    override val app: A,
) : PresenterBase {

    var view: CubeView? = null
        protected set

    suspend fun initialize(): CubeView {
        val v = onCreateView()
        view = v
        onInitialize()
        return v
    }

    override fun release() {
        view?.release()
        view = null
    }

    fun update() {
        view?.update()
    }

    protected abstract fun onCreateView(): CubeView

    protected abstract suspend fun onInitialize()
}
