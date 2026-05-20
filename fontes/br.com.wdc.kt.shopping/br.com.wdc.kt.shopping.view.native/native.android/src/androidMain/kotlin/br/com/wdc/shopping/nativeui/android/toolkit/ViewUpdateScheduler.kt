package br.com.wdc.shopping.nativeui.android.toolkit

import android.os.Handler
import android.os.Looper
import android.util.Log
import br.com.wdc.shopping.presentation.ShoppingApplication

/**
 * Centralized dirty-view scheduler for the Android platform.
 *
 * When a presenter calls view.update(), the view is marked dirty here.
 * A single Handler.post on the main looper is scheduled to flush all dirty views.
 * Before flushing, commitComputedState() gives every presenter a last chance
 * to mark additional views as dirty.
 */
object ViewUpdateScheduler {

    private val dirtyViews = linkedSetOf<AbstractViewAndroid<*>>()
    private var flushScheduled = false
    private var appProvider: (() -> ShoppingApplication?)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun initialize(appProvider: () -> ShoppingApplication?) {
        this.appProvider = appProvider
    }

    @Synchronized
    fun markDirty(view: AbstractViewAndroid<*>) {
        dirtyViews.add(view)
        if (!flushScheduled) {
            flushScheduled = true
            mainHandler.post { flush() }
        }
    }

    @Synchronized
    fun removeDirty(view: AbstractViewAndroid<*>) {
        dirtyViews.remove(view)
    }

    private fun flush() {
        appProvider?.invoke()?.commitComputedState()

        val snapshot: List<AbstractViewAndroid<*>>
        synchronized(this) {
            flushScheduled = false
            snapshot = dirtyViews.toList()
            dirtyViews.clear()
        }

        for (view in snapshot) {
            try {
                view.forceUpdate()
            } catch (e: Exception) {
                Log.e("ViewUpdateScheduler", "flush error", e)
            }
        }
    }
}
