package br.com.wdc.shopping.nativeui.web.bridge

import br.com.wdc.shopping.presentation.ShoppingApplication
import kotlinx.browser.window

/**
 * Centralized dirty-view scheduler for the native.web platform.
 *
 * When a presenter calls view.update(), the view is marked dirty here.
 * A single requestAnimationFrame callback is scheduled to flush all dirty views.
 * Before flushing, commitComputedState() gives every presenter a last chance
 * to mark additional views as dirty.
 */
object ViewUpdateScheduler {

    private val dirtyViews = linkedSetOf<ReactCubeView>()
    private var flushScheduled = false
    private var appProvider: (() -> ShoppingApplication?)? = null

    fun initialize(appProvider: () -> ShoppingApplication?) {
        this.appProvider = appProvider
    }

    fun markDirty(view: ReactCubeView) {
        dirtyViews.add(view)
        if (!flushScheduled) {
            flushScheduled = true
            window.requestAnimationFrame { flush() }
        }
    }

    fun removeDirty(view: ReactCubeView) {
        dirtyViews.remove(view)
    }

    internal fun flush() {
        // Snapshot and clear — new markDirty() calls after this point
        // will schedule the next flush cycle
        flushScheduled = false
        val snapshot = dirtyViews.toList()
        dirtyViews.clear()

        for (view in snapshot) {
            view.presenterBase.commitComputedState()
            view.notifyDirty()
        }
    }
}
