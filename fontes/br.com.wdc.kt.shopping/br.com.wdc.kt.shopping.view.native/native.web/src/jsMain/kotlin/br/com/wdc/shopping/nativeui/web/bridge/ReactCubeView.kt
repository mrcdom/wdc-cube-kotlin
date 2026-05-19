package br.com.wdc.shopping.nativeui.web.bridge

import br.com.wdc.framework.commons.log.Log
import kotlinx.browser.window
import react.FC
import react.Props

/**
 * Base class for React-backed views on the main thread.
 *
 * Each view keeps a revision counter. When the Worker sends a state update,
 * [readState] is called with the JSON, then [update] increments the revision
 * and triggers a React re-render.
 */
abstract class ReactCubeView(
    val instanceId: String,
    val proxy: WorkerProxy
) {

    /** Revision counter — incremented to signal React re-render. */
    var revision: Int = 0
        private set

    /** Callback set by the React root to trigger re-render on update(). */
    var onUpdate: (() -> Unit)? = null

    /** Whether a requestAnimationFrame is already scheduled. */
    private var frameScheduled: Boolean = false

    fun update() {
        revision++
        if (onUpdate != null && !frameScheduled) {
            frameScheduled = true
            window.requestAnimationFrame {
                frameScheduled = false
                onUpdate?.invoke()
            }
        }
    }

    /**
     * Deserialize state from JSON string received from the Worker.
     * Each view subclass implements this to populate its local typed state.
     */
    abstract fun readState(json: String)

    /** The React functional component that renders this view. */
    abstract val component: FC<Props>

    fun release() {
        onUpdate = null
    }

    /**
     * Send an action to this view's presenter in the Worker.
     */
    fun action(method: String, vararg args: Any?) {
        proxy.action(instanceId, method, *args)
    }

    companion object {
        internal val LOG = Log.getLogger("ReactCubeView")
    }
}
