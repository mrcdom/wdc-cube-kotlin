package br.com.wdc.shopping.nativeui.android.toolkit

import android.os.Handler
import android.os.Looper
import android.util.Log
import br.com.wdc.shopping.presentation.presenter.RootPresenter

/**
 * Centralized dirty-view scheduler for the Android platform.
 *
 * When a presenter calls view.update(), the view is marked dirty here.
 * A single Handler.post on the main looper is scheduled to flush all dirty views.
 * Each flush first runs the application's commitComputedState() — once per paint cycle,
 * right before the scopes are read — and then paints the dirty views, including the
 * ones that commitComputedState() itself marked dirty.
 */
object ViewUpdateScheduler {

    private val dirtyViews = linkedSetOf<AbstractViewAndroid<*>>()
    private var flushScheduled = false
    private var appProvider: (() -> RootPresenter?)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun initialize(appProvider: () -> RootPresenter?) {
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
        // flushScheduled is still true here, so views marked dirty now join this cycle
        try {
            appProvider?.invoke()?.commitComputedState()
        } catch (e: Exception) {
            Log.e("ViewUpdateScheduler", "commitComputedState error", e)
        }

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
