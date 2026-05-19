package br.com.wdc.shopping.nativeui.web.bridge

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.presentation.ShoppingApplication
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import react.FC
import react.Props

/**
 * Base class for React-backed CubeView implementations.
 *
 * Each view keeps a revision counter. When a presenter calls [update],
 * the counter increments and the React root is re-rendered, picking up
 * the new presenter state.
 */
abstract class ReactCubeView(
    private val id: String,
    val app: ShoppingApplication
) : CubeView {

    /** Revision counter — incremented by presenters to signal state changes. */
    var revision: Int = 0
        private set

    /** Callback set by the React root to trigger re-render on update(). */
    var onUpdate: (() -> Unit)? = null

    /** Whether a requestAnimationFrame is already scheduled. */
    private var frameScheduled: Boolean = false

    override val instanceId: String = id

    override fun update() {
        revision++
        if (onUpdate != null && !frameScheduled) {
            frameScheduled = true
            window.requestAnimationFrame {
                frameScheduled = false
                onUpdate?.invoke()
            }
        }
    }

    /** The React functional component that renders this view. */
    abstract val component: FC<Props>

    override fun release() {
        onUpdate = null
    }

    /**
     * Wraps a presenter call with error protection and dispatches it
     * on a single-threaded scope to ensure serial execution of presenter actions.
     */
    fun safeCall(action: () -> Unit) {
        presenterScope.launch {
            try {
                action()
            } catch (e: Exception) {
                app.alertUnexpectedError(LOG, "Erro inesperado em $id", e)
            }
        }
    }

    companion object {
        internal val LOG = Log.getLogger("ReactCubeView")

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
