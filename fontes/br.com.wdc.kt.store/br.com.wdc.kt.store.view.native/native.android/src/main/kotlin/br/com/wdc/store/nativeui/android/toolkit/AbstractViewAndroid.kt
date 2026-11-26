package br.com.wdc.store.nativeui.android.toolkit

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import br.com.wdc.framework.commons.log.Log as AppLog
import br.com.wdc.framework.cube.Scope
import br.com.wdc.store.i18n.Messages
import br.com.wdc.store.nativeui.android.AndroidNativeRootPresenter
import br.com.wdc.store.presentation.i18n.Message
import kotlinx.coroutines.launch

/**
 * Base class for all Android native views in the Cube MVP architecture.
 *
 * A view paints one [Scope] and calls its actions; it knows the application ([app]), never the presenter. The
 * application makes it the first time the scope is asked for ([AndroidNativeRootPresenter.viewOf]) and installs its
 * [updater] in the scope: a change marks the view dirty in [ViewUpdateScheduler], which coalesces the changes into
 * one doUpdate() per flush.
 *
 * A view that holds other scopes (a slot, the rows of a list) asks for their views with [viewOf] while it draws
 * ([redraw]); the views it did not ask for in a drawing are the ones whose scope left, and they are released — with
 * the views they held in turn, unless another view still holds them. That is how a scope that leaves the screen lets
 * go of its Android views: the presenters do not take part.
 */
abstract class AbstractViewAndroid<S : Scope>(
    private val viewId: String,
    protected val app: AndroidNativeRootPresenter,
    val scope: S
) {

    lateinit var rootView: View
        protected set

    /** What the scope is told to call on a change: kept to take back only this one. */
    val updater: () -> Unit = { update() }

    /** The views of the scopes this one holds. */
    private val children = LinkedHashSet<AbstractViewAndroid<*>>()

    /** The views that hold this one. */
    private val holders = HashSet<AbstractViewAndroid<*>>()

    /** The views asked for during the drawing under way; `null` outside one. */
    private var drawn: MutableSet<AbstractViewAndroid<*>>? = null

    /** Whether this view was let go of ([release]): it is never marked dirty nor drawn again. */
    @Volatile
    var released = false
        private set

    private var firstRender = true
    private val myListSlots = mutableListOf<ListSlot<*, *>>()

    val instanceId: String get() = viewId

    /** The UI texts in the device's language. */
    protected val messages: Messages get() = app.messages

    /** A presenter's [message] in the device's language; `null` when there is none. */
    protected fun translate(message: Message?): String? = message?.let { messages.text(it.key, it.args) }

    /** The scope changed: draw it again. */
    fun update() {
        if (released) return
        ViewUpdateScheduler.markDirty(this)
    }

    /** The view of a scope this one holds (a presenter's below, a panel, a row), asked for while drawing. */
    protected fun viewOf(held: Scope): AbstractViewAndroid<*> {
        val child = app.viewOf(held)
        child.holders += this
        children += child
        drawn?.add(child)
        return child
    }

    /**
     * Lets go of this view: the scope no longer tells it of changes, its view leaves its parent, and so do the views
     * it held that no other view holds.
     */
    fun release() {
        if (released) return
        released = true
        scope.clearUpdater(updater)
        app.forgetView(this)
        if (::rootView.isInitialized) (rootView.parent as? ViewGroup)?.removeView(rootView)
        onRelease()
        myListSlots.forEach { it.releaseAll() }
        myListSlots.clear()
        val held = children.toList()
        children.clear()
        for (child in held) child.dropHolder(this)
    }

    /** The view's own cleanup when it is released (listeners, timers). */
    protected open fun onRelease() {}

    private fun dropHolder(holder: AbstractViewAndroid<*>) {
        holders -= holder
        if (holders.isEmpty()) release()
    }

    abstract fun createView(): View

    abstract fun doUpdate()

    protected fun consumeFirstRender(): Boolean {
        if (firstRender) {
            firstRender = false
            return true
        }
        return false
    }

    fun initialize(): AbstractViewAndroid<S> {
        rootView = createView()
        redraw()
        return this
    }

    /**
     * Draws the scope now (the scheduler calls it for the dirty views); the held views not asked for while drawing
     * are released.
     */
    fun redraw() {
        if (released) return
        val asked = HashSet<AbstractViewAndroid<*>>()
        drawn = asked
        try {
            doUpdate()
        } catch (e: Exception) {
            Log.e("View[$viewId]", "doUpdate error", e)
        } finally {
            drawn = null
            for (child in children.toList()) {
                if (child !in asked) {
                    children -= child
                    child.dropHolder(this)
                }
            }
        }
    }

    protected fun safeAction(context: String, action: suspend () -> Unit) {
        app.presenterScope.launch {
            try {
                action()
            } catch (e: Exception) {
                app.alertUnexpectedError(LOG, "action '$context' error", e)
            }
        }
    }

    protected fun dp(value: Int): Int =
        (value * rootView.resources.displayMetrics.density + 0.5f).toInt()

    protected fun dpF(value: Int): Float =
        value * rootView.resources.displayMetrics.density

    // MARK: - Slots

    fun newViewSlot(container: FrameLayout): ViewSlot = ViewSlot(container)

    protected fun <T, V : AbstractViewAndroid<*>> newListSlot(
        container: ViewGroup,
        factory: () -> V,
        updater: (V, T) -> Unit
    ): ListSlot<T, V> {
        val slot = ListSlot(container, factory, updater)
        myListSlots.add(slot)
        return slot
    }

    class ViewSlot(private val container: FrameLayout) {
        var current: AbstractViewAndroid<*>? = null
            private set

        fun sync(newView: AbstractViewAndroid<*>?): Boolean {
            if (current === newView) return false
            container.removeAllViews()
            if (newView != null) {
                container.addView(
                    newView.rootView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
            current = newView
            return true
        }
    }

    class ListSlot<T, V : AbstractViewAndroid<*>>(
        private val container: ViewGroup,
        private val factory: () -> V,
        private val updater: (V, T) -> Unit
    ) {
        private val viewList = mutableListOf<V>()

        val size: Int get() = viewList.size

        fun sync(items: List<T>?) {
            val newSize = items?.size ?: 0
            val oldSize = viewList.size

            // Shrink
            if (oldSize > newSize) {
                for (i in oldSize - 1 downTo newSize) {
                    val view = viewList.removeAt(i)
                    container.removeView(view.rootView)
                    view.release()
                }
            }

            // Grow
            while (viewList.size < newSize) {
                val view = factory()
                viewList.add(view)
                container.addView(view.rootView, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            }

            // Update in-place
            if (items != null) {
                for (i in 0 until newSize) {
                    updater(viewList[i], items[i])
                }
            }
        }

        fun releaseAll() {
            viewList.forEach { it.release() }
            viewList.clear()
        }
    }

    companion object {
        private val LOG = AppLog.getLogger("AbstractViewAndroid")
    }
}
