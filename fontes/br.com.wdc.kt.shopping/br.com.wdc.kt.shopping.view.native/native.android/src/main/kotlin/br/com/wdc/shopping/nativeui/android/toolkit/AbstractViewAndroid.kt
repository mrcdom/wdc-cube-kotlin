package br.com.wdc.shopping.nativeui.android.toolkit

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import br.com.wdc.framework.commons.log.Log as AppLog
import br.com.wdc.framework.cube.CubeView
import br.com.wdc.framework.cube.Scope
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import kotlinx.coroutines.launch

/**
 * Base class for all Android native views in the Cube MVP architecture.
 *
 * A view paints one [Scope] and calls its actions; it knows the application ([app]) only to reach the views of child
 * scopes ([RootPresenter.viewOf]) and to launch actions on the presenter dispatcher.
 *
 * Implements dirty-flag update throttling: when the presenter calls update(), the view is marked dirty in
 * [ViewUpdateScheduler], which coalesces multiple update() calls into one doUpdate() per flush.
 */
abstract class AbstractViewAndroid<S : Scope>(
    private val viewId: String,
    protected val app: RootPresenter,
    protected val scope: S
) : CubeView {

    lateinit var rootView: View
        protected set

    private var released = false
    private var firstRender = true
    private val myListSlots = mutableListOf<ListSlot<*, *>>()

    override val instanceId: String get() = viewId

    override fun update() {
        if (released) return
        ViewUpdateScheduler.markDirty(this)
    }

    override fun release() {
        if (released) return
        released = true
        ViewUpdateScheduler.removeDirty(this)
        myListSlots.forEach { it.releaseAll() }
        myListSlots.clear()
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
        forceUpdate()
        return this
    }

    fun forceUpdate() {
        if (released) return
        try {
            doUpdate()
        } catch (e: Exception) {
            Log.e("View[$viewId]", "doUpdate error", e)
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
        var current: CubeView? = null
            private set

        fun sync(newView: CubeView?): Boolean {
            if (current === newView) return false
            container.removeAllViews()
            if (newView is AbstractViewAndroid<*>) {
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
