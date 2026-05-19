package br.com.wdc.shopping.nativeui.web.worker

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.CubeApplication

/**
 * Batches view update notifications and flushes them synchronously at the end
 * of each message handler cycle.
 *
 * When [markDirty] is called, the view is added to a dirty set.
 * [flush] must be called explicitly after all presenter logic completes.
 * Before serializing, [CubeApplication.commitComputedState] is invoked so that
 * presenters can finalize derived state.
 */
object WorkerUpdateScheduler {

    private val LOG = Log.getLogger("WorkerUpdateScheduler")

    private val dirtyViews = linkedSetOf<WorkerCubeView>()
    private var appProvider: (() -> CubeApplication?)? = null

    fun initialize(appProvider: () -> CubeApplication?) {
        this.appProvider = appProvider
    }

    fun markDirty(view: WorkerCubeView) {
        dirtyViews.add(view)
    }

    fun removeDirty(view: WorkerCubeView) {
        dirtyViews.remove(view)
    }

    /**
     * Flushes all pending state updates. Call at the end of each message handler.
     */
    fun flush() {
        if (dirtyViews.isEmpty()) return

        try {
            appProvider?.invoke()?.commitComputedState()
        } catch (e: Exception) {
            LOG.error("commitComputedState failed", e)
        }

        val snapshot = dirtyViews.toList()
        dirtyViews.clear()

        for (view in snapshot) {
            try {
                view.flushState()
            } catch (e: Exception) {
                LOG.error("flushState failed for viewId=${view.instanceId}", e)
            }
        }
    }
}
