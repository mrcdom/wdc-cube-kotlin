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

    /**
     * Makes [scope] repaint this presenter's view on its own: each change of a [Scope] field calls
     * [update]. Use it on the presenter's scope: `val scope = bind(CartScope())`.
     */
    protected fun <S : Scope> bind(scope: S): S = scope.also { it.observe(::update) }

    protected abstract fun onCreateView(): CubeView

    protected abstract suspend fun onInitialize()
}
