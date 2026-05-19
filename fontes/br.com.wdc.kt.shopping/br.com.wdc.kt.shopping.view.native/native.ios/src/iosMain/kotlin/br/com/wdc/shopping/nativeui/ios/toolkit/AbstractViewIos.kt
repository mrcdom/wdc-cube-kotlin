package br.com.wdc.shopping.nativeui.ios.toolkit

import br.com.wdc.framework.cube.CubeView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLog
import platform.UIKit.UIStackView
import platform.UIKit.UIView
import platform.UIKit.NSLayoutConstraint

/**
 * Base class for all iOS native views in the Cube MVP architecture.
 *
 * Implements dirty-flag update throttling: when the presenter calls update(),
 * a dirty flag is set and the actual doUpdate() is scheduled on the main
 * run-loop via DispatchQueue.main.async (coalesces multiple calls in the same frame).
 *
 * Subclasses implement:
 * - createView(): build the UIView hierarchy (called once — the "template")
 * - doUpdate(): reconcile presenter state → UIKit (called at most once per frame)
 *
 * The doUpdate() should use guards to only update components whose data has actually changed.
 */
@OptIn(ExperimentalForeignApi::class)
abstract class AbstractViewIos<P>(
    private val viewId: String,
    protected val presenter: P
) : CubeView {

    companion object {
        /**
         * Global GC root: UIGestureRecognizer and UIControl store their targets as
         * unretained pointers (invisible to Kotlin/Native GC tracing).
         * This static set is always reachable by the GC, preventing collection.
         * Views remove their objects in release() to avoid leaks.
         */
        private val gcRoots = mutableSetOf<Any>()
    }

    private val myGcRetained = mutableListOf<Any>()
    private val myListSlots = mutableListOf<ListSlot<*, *>>()

    protected fun retainForGC(obj: Any) {
        myGcRetained.add(obj)
        gcRoots.add(obj)
    }

    /** The root UIView for this CubeView */
    lateinit var rootView: UIView
        protected set

    private var released = false
    private var firstRender = true

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
        myGcRetained.forEach { gcRoots.remove(it) }
        myGcRetained.clear()
    }

    /**
     * Build the UIView hierarchy. Called once during initialization.
     * This is the "template" — creates the static structure.
     */
    abstract fun createView(): UIView

    /**
     * Reconcile presenter state to UIKit views. Called at most once per frame.
     * Only runs when dirty flag is set.
     *
     * Implementations should guard each section with change detection:
     *   if (field !== lastField) { lastField = field; ... apply changes ... }
     */
    abstract fun doUpdate()

    /**
     * Returns true on the very first doUpdate call, then false.
     * Useful for views that need to distinguish initialization from subsequent updates.
     */
    protected fun consumeFirstRender(): Boolean {
        if (firstRender) {
            firstRender = false
            return true
        }
        return false
    }

    /**
     * Initialize the view: creates the hierarchy and forces the first update.
     */
    fun initialize(): AbstractViewIos<P> {
        rootView = createView()
        forceUpdate()
        return this
    }

    /**
     * Force immediate update (bypasses scheduler).
     */
    fun forceUpdate() {
        if (released) return
        try {
            doUpdate()
        } catch (e: Exception) {
            NSLog("AbstractViewIos[$viewId] doUpdate error: ${e.message}")
        }
    }

    /**
     * Wrap a user-initiated action in error handling.
     * Logs and swallows exceptions to prevent crashes from UI interactions.
     */
    protected fun safeAction(context: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            NSLog("AbstractViewIos[$viewId] action '$context' error: ${e.message}")
        }
    }

    // MARK: - Slot Helpers

    /**
     * Creates a single-child view slot.
     * When the slot's view changes, removes old child and adds the new one filling the container.
     */
    fun newViewSlot(container: UIView): ViewSlot {
        return ViewSlot(container)
    }

    /**
     * A single-child view slot backed by a UIView container.
     */
    class ViewSlot(private val container: UIView) {
        var current: CubeView? = null
            private set

        /**
         * Synchronize the slot: if newView differs from current, swap.
         * Returns true if the view changed.
         */
        fun sync(newView: CubeView?): Boolean {
            if (current === newView) return false
            val oldSubviews = container.subviews.toList()
            oldSubviews.forEach { (it as? UIView)?.removeFromSuperview() }
            if (newView is AbstractViewIos<*>) {
                val childView = newView.rootView
                childView.translatesAutoresizingMaskIntoConstraints = false
                container.addSubview(childView)
                NSLayoutConstraint.activateConstraints(listOf(
                    childView.topAnchor.constraintEqualToAnchor(container.topAnchor),
                    childView.leadingAnchor.constraintEqualToAnchor(container.leadingAnchor),
                    childView.trailingAnchor.constraintEqualToAnchor(container.trailingAnchor),
                    childView.bottomAnchor.constraintEqualToAnchor(container.bottomAnchor)
                ))
            }
            current = newView
            return true
        }
    }

    /**
     * Creates a list slot that efficiently syncs items to views.
     * Uses grow/shrink at edges + update-in-place (same algorithm as Gluon).
     */
    protected fun <T, V : AbstractViewIos<*>> newListSlot(
        container: UIStackView,
        factory: () -> V,
        updater: (V, T) -> Unit
    ): ListSlot<T, V> {
        val slot = ListSlot(container, factory, updater)
        myListSlots.add(slot)
        return slot
    }

    /**
     * A list slot that recycles item views — grows/shrinks at edges, updates in-place.
     */
    class ListSlot<T, V : AbstractViewIos<*>>(
        private val container: UIStackView,
        private val factory: () -> V,
        private val updater: (V, T) -> Unit
    ) {
        private val viewList = mutableListOf<V>()

        val size: Int get() = viewList.size

        fun sync(items: List<T>?) {
            val newSize = items?.size ?: 0
            val oldSize = viewList.size

            if (oldSize > newSize) {
                for (i in oldSize - 1 downTo newSize) {
                    val view = viewList.removeAt(i)
                    view.rootView.removeFromSuperview()
                    view.release()
                }
            }

            while (viewList.size < newSize) {
                val view = factory()
                viewList.add(view)
                container.addArrangedSubview(view.rootView)
            }

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
}
