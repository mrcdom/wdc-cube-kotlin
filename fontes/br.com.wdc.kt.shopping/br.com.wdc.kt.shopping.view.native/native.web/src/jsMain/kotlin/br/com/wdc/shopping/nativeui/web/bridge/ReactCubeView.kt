package br.com.wdc.shopping.nativeui.web.bridge

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.Scope
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import kotlinx.coroutines.launch
import react.FC
import react.Props

/**
 * Base class for React-backed CubeView implementations: draws one [scope], created by the application's catalog
 * ([br.com.wdc.framework.cube.CubeApplication.viewOf]).
 *
 * Each view keeps a revision counter. When the scope asks for a repaint ([update]), the view is marked dirty in
 * [ViewUpdateScheduler]; on the flush the counter increments and the view's React component re-renders, reading the
 * scope as it is then.
 */
abstract class ReactCubeView<S : Scope>(
    private val id: String,
    val app: RootPresenter,
    val scope: S,
) : CubeView {

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

    /**
     * Repaints this view only, without going through the scope: for UI state the view keeps in its own fields
     * (a quantity being chosen, the selected tab), changed by an emitter. The remote React client's
     * `BaseViewClass.forceUpdate` does the same.
     */
    protected fun forceUpdate() {
        notifyDirty()
    }

    /** The React functional component that renders this view. */
    abstract val component: FC<Props>

    override fun release() {
        ViewUpdateScheduler.removeDirty(this)
        onUpdate = null
    }

    /**
     * Wraps a call to the scope's actions with error protection and dispatches it
     * on the presenters' single-threaded scope to ensure serial execution of presenter actions.
     */
    fun safeCall(action: suspend () -> Unit) {
        app.presenterScope.launch {
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
        internal val LOG = Log.getLogger("ReactCubeView")
    }
}
