package br.com.wdc.shopping.nativeui.ios.toolkit

import platform.Foundation.NSLog
import platform.Foundation.NSLock
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Centralized dirty-view scheduler for the iOS platform.
 *
 * When a presenter calls view.update(), the view is marked dirty here.
 * A single dispatch_async(main_queue) callback is scheduled to flush all dirty views.
 * Before flushing, commitComputedState() gives every presenter a last chance
 * to mark additional views as dirty.
 *
 * Thread safety: markDirty/removeDirty use NSLock since update() may
 * be called from any thread. The flush always runs on the main queue.
 */
object ViewUpdateScheduler {

    private val dirtyViews = linkedSetOf<AbstractViewIos<*>>()
    private var flushScheduled = false
    private val lock = NSLock()

    fun markDirty(view: AbstractViewIos<*>) {
        lock.lock()
        try {
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
                view.presenterBase.commitComputedState()
                view.forceUpdate()
            } catch (e: Exception) {
                NSLog("ViewUpdateScheduler flush error: ${e.message}")
            }
        }
    }
}
