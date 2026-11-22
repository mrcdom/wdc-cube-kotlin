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
     * Makes [state] repaint this presenter's view on its own: each change of an [ObservableViewState] field calls
     * [update]. Use it on the presenter's state: `val state = observed(CartViewState())`.
     */
    protected fun <S : ObservableViewState> observed(state: S): S = state.also { it.observe(::update) }

    protected abstract fun onCreateView(): CubeView

    protected abstract suspend fun onInitialize()
}
