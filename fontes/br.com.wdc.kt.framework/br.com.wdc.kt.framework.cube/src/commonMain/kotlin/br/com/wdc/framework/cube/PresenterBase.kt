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

    /**
     * Computes the calculated/derived fields the view reads from the presenter state.
     *
     * Called by the view layer exactly once per paint cycle, right before the view
     * state is read for rendering. The view ignores its own `update()` during this call.
     */
    fun commitComputedState() {}

    fun release()
}
