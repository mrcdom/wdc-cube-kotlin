package br.com.wdc.shopping.nativeui.web.bridge

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.PresenterBase
import br.com.wdc.shopping.presentation.ShoppingApplication
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
    internal val presenterBase: PresenterBase
) : CubeView {

    val app: ShoppingApplication get() = presenterBase.app as ShoppingApplication

    /** Revision counter — incremented during flush to signal state changes. */
    var revision: Int = 0
        internal set

    /** Callback set by the React root to trigger re-render on update(). */
    var onUpdate: (() -> Unit)? = null

    override val instanceId: String = id

    override fun update() {
        ViewUpdateScheduler.markDirty(this)
    }

    /**
     * Called by ViewUpdateScheduler during flush to actually notify this view.
     * Increments the revision and invokes the React re-render callback.
     */
    internal fun notifyDirty() {
        revision++
        onUpdate?.invoke()
    }

    /** The React functional component that renders this view. */
    abstract val component: FC<Props>

    override fun release() {
        ViewUpdateScheduler.removeDirty(this)
        onUpdate = null
    }

    /**
     * Wraps a presenter call with error protection and dispatches it
     * on a single-threaded scope to ensure serial execution of presenter actions.
     */
    fun safeCall(action: suspend () -> Unit) {
        app.presenterScope.launch {
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
        internal val LOG = Log.getLogger("ReactCubeView")
    }
}
