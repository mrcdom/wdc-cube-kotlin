package br.com.wdc.store.nativeui.web.bridge

import br.com.wdc.store.presentation.presenter.RootPresenter
import kotlinx.browser.window

/**
 * Centralized dirty-view scheduler for the native.web platform.
 *
 * When a scope asks for a repaint (view.update()), the view is marked dirty here.
 * A single requestAnimationFrame callback is scheduled to flush all dirty views.
 * Each flush first runs the application's commitComputedState() — once per paint cycle,
 * right before painting — so every presenter computes the derived fields its view reads;
 * whatever that changes marks views dirty and is painted in the same flush.
 */
object ViewUpdateScheduler {

    private val dirtyViews = linkedSetOf<ReactCubeView<*>>()
    private var flushScheduled = false
    private var committing = false
    private var appProvider: (() -> RootPresenter?)? = null

    fun initialize(appProvider: () -> RootPresenter?) {
        this.appProvider = appProvider
    }

    fun markDirty(view: ReactCubeView<*>) {
        dirtyViews.add(view)
        // marked while the flush commits the computed state: painted by this same flush
        if (committing) return
        if (!flushScheduled) {
            flushScheduled = true
            window.requestAnimationFrame { flush() }
        }
    }

    fun removeDirty(view: ReactCubeView<*>) {
        dirtyViews.remove(view)
    }

    internal fun flush() {
        // Once per paint cycle, before reading the scopes: the views it marks dirty join this flush
        committing = true
        try {
            appProvider?.invoke()?.commitComputedState()
        } finally {
            committing = false
        }

        // Snapshot and clear — new markDirty() calls after this point
        // will schedule the next flush cycle
        flushScheduled = false
        val snapshot = dirtyViews.toList()
        dirtyViews.clear()

        for (view in snapshot) {
            view.notifyDirty()
        }
    }
}
