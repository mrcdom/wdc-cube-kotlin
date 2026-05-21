package br.com.wdc.shopping.view.compose.bridge

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.shopping.presentation.ShoppingApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Centralized dirty-view scheduler for the Compose platform.
 *
 * When a presenter calls view.update(), the view is marked dirty here.
 * After safeCall completes, flush() is invoked — it calls commitComputedState()
 * giving every presenter a last chance to mark additional views as dirty, then
 * increments the revision counter for each dirty view (triggering recomposition).
 *
 * Thread safety: All operations (markDirty, removeDirty, flush) execute on the
 * single-threaded presenterScope, so no locking is needed. External callers that
 * wish to invoke update() from outside presenterScope must launch on it first.
 */
object ViewUpdateScheduler {

    private val LOG = Log.getLogger("ViewUpdateScheduler")

    private val dirtyViews = linkedSetOf<ComposeCubeView>()
    private var appProvider: (() -> ShoppingApplication?)? = null

    /**
     * Returns the presenter scope from the current application instance.
     * This ensures each application instance has its own serialized scope.
     */
    internal val presenterScope: CoroutineScope
        get() = appProvider!!.invoke()!!.presenterScope

    /**
     * Launch a presenter action on the serial presenterScope.
     * Use from platform entry points that need to call suspend navigation methods.
     */
    fun launchPresenterAction(action: suspend () -> Unit) {
        presenterScope.launch { action() }
    }

    fun initialize(appProvider: () -> ShoppingApplication?) {
        this.appProvider = appProvider
    }

    /**
     * Marks a view as dirty. Must be called from presenterScope.
     */
    fun markDirty(view: ComposeCubeView) {
        dirtyViews.add(view)
    }

    /**
     * Removes a view from the dirty set. Safe to call from any context via launch.
     */
    fun removeDirty(view: ComposeCubeView) {
        presenterScope.launch {
            dirtyViews.remove(view)
        }
    }

    /**
     * Flushes all dirty views. Called at the end of every safeCall on presenterScope.
     * Calls commitComputedState() per dirty view, then notifies (increments revision for) each.
     */
    internal fun flush() {
        // Snapshot and clear
        val snapshot = dirtyViews.toList()
        dirtyViews.clear()

        for (view in snapshot) {
            try {
                view.presenterBase.commitComputedState()
            } catch (e: Exception) {
                LOG.error("commitComputedState error for ${view.instanceId}: ${e.message}", e)
            }
            view.revision.value++
        }
    }
}
