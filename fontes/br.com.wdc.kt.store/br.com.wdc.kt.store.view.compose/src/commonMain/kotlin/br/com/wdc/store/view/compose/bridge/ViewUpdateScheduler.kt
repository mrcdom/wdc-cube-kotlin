package br.com.wdc.store.view.compose.bridge

import androidx.compose.runtime.MutableIntState
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.store.presentation.presenter.RootPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The paint cycle of the Compose platform.
 *
 * A scope that changes marks dirty the views on screen that read it ([useScope]), and a flush is scheduled on the
 * presenters' dispatcher (once per round, like requestAnimationFrame on the web). An update made outside any view
 * action — the initial navigation, the continuation of a parallel launch, a timer — therefore still reaches the
 * screen; [safeCall] also flushes when the action ends. The flush runs the application's commitComputedState() once,
 * so the presenters compute the derived fields the views read, and then bumps the paint counter of each dirty view,
 * which recomposes it.
 *
 * Thread safety: the dirty set is touched only on the presenters' single-threaded dispatcher; a caller on another
 * thread (the composition) goes through [markDirtyLater] and [removeDirty], which launch on it.
 */
object ViewUpdateScheduler {

    private val LOG = Log.getLogger("ViewUpdateScheduler")

    private val dirtyViews = linkedSetOf<MutableIntState>()
    private var flushScheduled = false
    private var appProvider: (() -> RootPresenter?)? = null

    private val app: RootPresenter get() = appProvider!!.invoke()!!

    /** The application's serial dispatcher: each application instance has its own. */
    internal val presenterScope: CoroutineScope get() = app.presenterScope

    /**
     * Launch a presenter action on the serial presenterScope.
     * Use from platform entry points that need to call suspend navigation methods.
     */
    fun launchPresenterAction(action: suspend () -> Unit) {
        presenterScope.launch { action() }
    }

    fun initialize(appProvider: () -> RootPresenter?) {
        this.appProvider = appProvider
    }

    /** Marks a view dirty. Called on presenterScope (where the scopes change). */
    internal fun markDirty(view: MutableIntState) {
        dirtyViews.add(view)
        if (!flushScheduled) {
            val scope = appProvider?.invoke()?.presenterScope ?: return
            flushScheduled = true
            scope.launch { flush() }
        }
    }

    /** Marks a view dirty from another thread. */
    internal fun markDirtyLater(view: MutableIntState) {
        appProvider?.invoke()?.presenterScope?.launch { markDirty(view) }
    }

    /** A view left the screen. Safe to call from any thread. */
    internal fun removeDirty(view: MutableIntState) {
        appProvider?.invoke()?.presenterScope?.launch { dirtyViews.remove(view) }
    }

    internal fun safeCall(action: suspend () -> Unit) {
        val app = app
        app.presenterScope.launch {
            try {
                action()
            } catch (e: Exception) {
                app.alertUnexpectedError(LOG, "Unexpected error", e)
            } finally {
                flush()
            }
        }
    }

    /**
     * Paints the dirty views. Runs the application's commitComputedState() once, before taking them (what it changes
     * marks views dirty and joins this same cycle), then bumps each one's paint counter.
     */
    internal fun flush() {
        flushScheduled = false
        if (dirtyViews.isEmpty()) return

        try {
            appProvider?.invoke()?.commitComputedState()
        } catch (e: Exception) {
            LOG.error("commitComputedState error: ${e.message}", e)
        }

        val snapshot = dirtyViews.toList()
        dirtyViews.clear()

        for (view in snapshot) {
            view.intValue++
        }
    }
}
