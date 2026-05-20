package br.com.wdc.shopping.view.compose.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.ShoppingApplication
import kotlinx.coroutines.launch

/**
 * Base class for Compose-backed CubeView implementations.
 *
 * Coalesces multiple update() calls within the same cycle into a single
 * Compose recomposition via the centralized ViewUpdateScheduler. The scheduler
 * batches dirty views, calls commitComputedState(), then increments revision
 * for each dirty view — triggering a single recomposition pass.
 */
abstract class ComposeCubeView(
    private val id: String,
    protected val app: ShoppingApplication
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
     * Wraps a presenter call with error protection and dispatches it off the UI thread.
     *
     * Uses the ViewUpdateScheduler's single-threaded presenterScope to ensure serial
     * execution of presenter actions, preserving the synchronous semantics of the
     * Cube MVP pattern while keeping the UI thread free.
     */
    protected fun safeCall(action: () -> Unit) {
        ViewUpdateScheduler.presenterScope.launch {
            try {
                action()
            } catch (e: Exception) {
                app.alertUnexpectedError(LOG, "Erro inesperado em $id", e)
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
 * Renders a CubeView slot by casting to ComposeCubeView and calling Render().
 */
@Composable
fun RenderSlot(view: CubeView) {
    (view as? ComposeCubeView)?.Render()
}
