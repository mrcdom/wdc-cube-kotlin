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
     * Makes [scope] repaint this presenter's view on its own: each change of a [Scope] field calls
     * [update]. Use it on the presenter's scope: `val scope = bind(CartScope())`.
     */
    protected fun <S : Scope> bind(scope: S): S = scope.also { it.observe(::update) }

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean = true

}
