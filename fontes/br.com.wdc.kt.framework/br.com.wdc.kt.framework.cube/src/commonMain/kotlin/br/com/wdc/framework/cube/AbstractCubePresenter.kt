package br.com.wdc.framework.cube

/** A presenter of a place: navigation creates it, steps it with [applyParameters] and releases it. */
open class AbstractCubePresenter<A : CubeApplication>(app: A) : AbstractPresenter<A>(app), CubePresenter {

    override suspend fun applyParameters(intent: CubeIntent, initialization: Boolean, deepest: Boolean): Boolean = true
}
