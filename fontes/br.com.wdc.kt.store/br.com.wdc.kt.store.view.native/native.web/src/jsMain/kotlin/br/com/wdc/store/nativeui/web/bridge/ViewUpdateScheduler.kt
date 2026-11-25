package br.com.wdc.store.nativeui.web.bridge

import br.com.wdc.store.presentation.presenter.RootPresenter
import kotlinx.browser.window

/**
 * The paint cycle of the native.web platform.
 *
 * A scope that changes marks dirty the view on screen that draws it (its updater, installed by [useScope]); a single
 * requestAnimationFrame callback is scheduled to paint them. Each flush first runs the application's
 * commitComputedState() — once per paint cycle, right before painting — so every presenter computes the derived
 * fields its view reads; whatever that changes marks views dirty and is painted in the same flush.
 */
object ViewUpdateScheduler {

    private val dirtyViews = linkedSetOf<() -> Unit>()
    private var flushScheduled = false
    private var committing = false
    private var appProvider: (() -> RootPresenter?)? = null

    internal val app: RootPresenter get() = appProvider!!.invoke()!!

    fun initialize(appProvider: () -> RootPresenter?) {
        this.appProvider = appProvider
    }

    /** Marks a view dirty: [repaint] is its component's repaint. */
    fun markDirty(repaint: () -> Unit) {
        dirtyViews.add(repaint)
        // marked while the flush commits the computed state: painted by this same flush
        if (committing) return
        if (!flushScheduled) {
            flushScheduled = true
            window.requestAnimationFrame { flush() }
        }
    }

    /** A view left the screen. */
    fun removeDirty(repaint: () -> Unit) {
        dirtyViews.remove(repaint)
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

        for (repaint in snapshot) {
            repaint()
        }
    }
}
