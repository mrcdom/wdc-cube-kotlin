package br.com.wdc.store.nativeui.ios.toolkit

import br.com.wdc.framework.cube.CubeApplication
import platform.Foundation.NSLog
import platform.Foundation.NSLock
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Centralized dirty-view scheduler for the iOS platform.
 *
 * When a scope changes, it calls the updater its view installed, and the view is marked dirty here.
 * A single dispatch_async(main_queue) callback is scheduled to flush all dirty views.
 * Each flush first runs the application's commitComputedState() — once per paint cycle,
 * right before painting — so the presenters compute the derived fields the views read;
 * views it marks dirty join the same flush. Then the dirty views are painted.
 *
 * Thread safety: markDirty/removeDirty use NSLock since update() may
 * be called from any thread. The flush always runs on the main queue.
 */
object ViewUpdateScheduler {

    private val dirtyViews = linkedSetOf<AbstractViewIos<*>>()
    private var flushScheduled = false
    private val lock = NSLock()

    /** The application whose computed state is committed before each flush (the one of the views marked dirty). */
    private var app: CubeApplication? = null

    fun markDirty(view: AbstractViewIos<*>, app: CubeApplication) {
        lock.lock()
        try {
            this.app = app
            dirtyViews.add(view)
            if (!flushScheduled) {
                flushScheduled = true
                dispatch_async(dispatch_get_main_queue()) { flush() }
            }
        } finally {
            lock.unlock()
        }
    }

    fun removeDirty(view: AbstractViewIos<*>) {
        lock.lock()
        try {
            dirtyViews.remove(view)
        } finally {
            lock.unlock()
        }
    }

    private fun flush() {
        // Derived state first: what it changes marks views dirty and is painted in this same flush
        val application: CubeApplication?
        lock.lock()
        try {
            application = app
        } finally {
            lock.unlock()
        }
        try {
            application?.commitComputedState()
        } catch (e: Exception) {
            NSLog("ViewUpdateScheduler commitComputedState error: ${e.message}")
        }

        // Snapshot and clear
        val snapshot: List<AbstractViewIos<*>>
        lock.lock()
        try {
            flushScheduled = false
            snapshot = dirtyViews.toList()
            dirtyViews.clear()
        } finally {
            lock.unlock()
        }

        for (view in snapshot) {
            try {
                view.redraw()
            } catch (e: Exception) {
                NSLog("ViewUpdateScheduler flush error: ${e.message}")
            }
        }
    }
}
