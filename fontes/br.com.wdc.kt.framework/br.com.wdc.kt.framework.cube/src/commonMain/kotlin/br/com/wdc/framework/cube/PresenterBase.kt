package br.com.wdc.framework.cube

/**
 * Common contract for all presenters in the Cube architecture.
 * Guarantees access to the application instance and lifecycle release.
 *
 * Implemented by both [CubePresenter] (routed presenters) and
 * [AbstractChildPresenter] (embedded/child presenters).
 */
interface PresenterBase {
    val app: CubeApplication
    fun commitComputedState() {}
    fun release()
}
