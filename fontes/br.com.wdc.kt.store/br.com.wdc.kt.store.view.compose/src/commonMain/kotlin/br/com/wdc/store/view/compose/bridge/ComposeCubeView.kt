package br.com.wdc.store.view.compose.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.Scope
import br.com.wdc.store.presentation.presenter.RootPresenter
import kotlinx.coroutines.launch

/**
 * Base class for Compose-backed CubeView implementations: draws one [scope] of the application [app].
 *
 * Coalesces multiple update() calls within the same cycle into a single
 * Compose recomposition via the centralized ViewUpdateScheduler. The scheduler
 * runs the application's commitComputedState() once per paint cycle, then
 * increments the revision of each dirty view — triggering a single recomposition pass.
 */
abstract class ComposeCubeView<S : Scope>(
    private val id: String,
    protected val app: RootPresenter,
    protected val scope: S,
) : CubeView {

    /** Revision counter — read this in @Composable functions to trigger recomposition. */
    val revision: MutableState<Int> = mutableIntStateOf(0)

    override val instanceId: String get() = id

    override fun update() {
        ViewUpdateScheduler.markDirty(this)
    }

    @Composable
    abstract fun Render()

    override fun release() {
        ViewUpdateScheduler.removeDirty(this)
    }

    /**
     * Wraps a scope action call with error protection and dispatches it off the UI thread.
     *
     * Uses the ViewUpdateScheduler's single-threaded presenterScope to ensure serial
     * execution of presenter actions, preserving the synchronous semantics of the
     * Cube MVP pattern while keeping the UI thread free.
     */
    protected fun safeCall(action: suspend () -> Unit) {
        ViewUpdateScheduler.presenterScope.launch {
            try {
                action()
            } catch (e: Exception) {
                app.alertUnexpectedError(LOG, "Unexpected error in $id", e)
            } finally {
                ViewUpdateScheduler.flush()
            }
        }
    }

    companion object {
        @PublishedApi
        internal val LOG = Log.getLogger("ComposeCubeView")
    }
}

/**
 * Renders a CubeView slot by casting to ComposeCubeView and calling Render(). Views come from
 * [RootPresenter.viewOf][br.com.wdc.framework.cube.CubeApplication.viewOf], which gives the same instance for the same
 * scope, so the Compose compiler (strong skipping) skips this call while the slot keeps its scope.
 */
@Composable
fun RenderSlot(view: CubeView) {
    (view as? ComposeCubeView<*>)?.Render()
}
