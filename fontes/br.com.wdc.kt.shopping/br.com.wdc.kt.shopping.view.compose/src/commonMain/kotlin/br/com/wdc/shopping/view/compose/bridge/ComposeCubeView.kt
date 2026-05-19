package br.com.wdc.shopping.view.compose.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.ShoppingApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Base class for Compose-backed CubeView implementations.
 *
 * Coalesces multiple update() calls within the same cycle into a single
 * Compose recomposition by incrementing a revision counter. Compose only
 * reads the revision state during recomposition, so multiple increments
 * between frames result in a single recomposition.
 */
abstract class ComposeCubeView(
    private val id: String,
    protected val app: ShoppingApplication
) : CubeView {

    /** Revision counter — read this in @Composable functions to trigger recomposition. */
    val revision: MutableState<Int> = mutableIntStateOf(0)

    override val instanceId: String get() = id

    override fun update() {
        revision.value++
    }

    @Composable
    abstract fun Render()

    override fun release() {
        // No-op by default; Compose manages its own lifecycle
    }

    /**
     * Wraps a presenter call with error protection and dispatches it off the UI thread.
     *
     * Uses a single-threaded dispatcher to ensure serial execution of presenter actions,
     * preserving the synchronous semantics of the Cube MVP pattern while keeping the
     * UI thread free. The presenter's call to view.update() triggers Compose recomposition
     * back on the UI thread via MutableState.
     */
    protected fun safeCall(action: () -> Unit) {
        presenterScope.launch {
            try {
                action()
            } catch (e: Exception) {
                app.alertUnexpectedError(LOG, "Erro inesperado em $id", e)
            }
        }
    }

    companion object {
        @PublishedApi
        internal val LOG = Log.getLogger("ComposeCubeView")

        /**
         * Single-threaded coroutine scope for presenter actions.
         * limitedParallelism(1) guarantees serial execution, avoiding
         * concurrency issues in presenter state.
         */
        private val presenterScope = CoroutineScope(
            Dispatchers.Default.limitedParallelism(1)
        )
    }
}

/**
 * Renders a CubeView slot by casting to ComposeCubeView and calling Render().
 */
@Composable
fun RenderSlot(view: CubeView) {
    (view as? ComposeCubeView)?.Render()
}
