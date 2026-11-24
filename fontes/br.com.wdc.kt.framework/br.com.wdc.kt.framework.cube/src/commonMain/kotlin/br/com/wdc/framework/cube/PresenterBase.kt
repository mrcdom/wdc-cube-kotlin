package br.com.wdc.framework.cube

/**
 * Common contract for all presenters in the Cube architecture.
 * Guarantees access to the application instance and lifecycle release.
 *
 * Implemented by every presenter ([AbstractPresenter]) and by the application's root presenter.
 */
interface PresenterBase {
    val app: CubeApplication

    /**
     * Computes the calculated/derived fields the view reads from the presenter's scope.
     *
     * Called once per paint cycle, right before the scopes are read for painting: the view layer runs the
     * application's `commitComputedState()`, which runs every live presenter's.
     */
    fun commitComputedState() {}

    fun release()
}
