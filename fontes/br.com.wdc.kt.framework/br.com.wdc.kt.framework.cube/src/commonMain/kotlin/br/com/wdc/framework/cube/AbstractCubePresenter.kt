package br.com.wdc.framework.cube

open class AbstractCubePresenter<A : CubeApplication>(
    override val app: A,
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

    /**
     * Makes [state] repaint this presenter's view on its own: each change of an [ObservableViewState] field calls
     * [update]. Use it on the presenter's state: `val state = observed(CartViewState())`.
     */
    protected fun <S : ObservableViewState> observed(state: S): S = state.also { it.observe(::update) }

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean = true

}
