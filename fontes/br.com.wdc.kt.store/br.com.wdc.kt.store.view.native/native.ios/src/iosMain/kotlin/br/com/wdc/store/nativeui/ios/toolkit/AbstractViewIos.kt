package br.com.wdc.store.nativeui.ios.toolkit

import br.com.wdc.store.presentation.i18n.Message
import br.com.wdc.store.i18n.Messages
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.Scope
import br.com.wdc.store.nativeui.ios.IosNativeRootPresenter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import platform.Foundation.NSLog
import platform.UIKit.UIStackView
import platform.UIKit.UIView
import platform.UIKit.NSLayoutConstraint

/** Shared contract for slot types that need cleanup on release. */
interface Releasable {
    fun releaseAll()
}

/**
 * Base class for all iOS native views in the Cube MVP architecture.
 *
 * A view draws one [Scope]; it knows the application ([app]) and its scope, never the presenter. The application
 * makes it the first time the scope is asked for ([IosNativeRootPresenter.viewOf]) and installs its [updater] in the
 * scope: a change marks the view dirty, and the scheduler redraws it once per run-loop pass ([ViewUpdateScheduler]).
 *
 * A view that holds other scopes (a slot, the rows of a list) asks for their views with [viewOf] while it draws
 * ([redraw]); the views it did not ask for in a drawing are the ones whose scope left, and they are released — with
 * the views they held in turn, unless another view still holds them. That is how a scope that leaves the screen lets
 * go of its UIKit views: the presenters do not take part.
 *
 * Subclasses implement:
 * - createView(): build the UIView hierarchy (called once — the "template")
 * - doUpdate(): reconcile the scope → UIKit (called at most once per frame)
 *
 * The doUpdate() should use guards to only update components whose data has actually changed.
 */
@OptIn(ExperimentalForeignApi::class)
abstract class AbstractViewIos<S : Scope>(
    private val viewId: String,
    protected val app: IosNativeRootPresenter,
    val scope: S
) {

    companion object {
        /**
         * Global GC root: UIGestureRecognizer and UIControl store their targets as
         * unretained pointers (invisible to Kotlin/Native GC tracing).
         * This static set is always reachable by the GC, preventing collection.
         * Views remove their objects in release() to avoid leaks.
         */
        private val gcRoots = mutableSetOf<Any>()
        private val LOG = Log.getLogger("AbstractViewIos")
    }

    /** The UI texts, in the app's language. */
    protected val messages: Messages get() = app.messages

    /** The text of a message of the presentation, in the app's language. */
    protected fun textOf(message: Message): String = messages.text(message.key, message.args)

    private val myGcRetained = mutableListOf<Any>()
    private val myListSlots = mutableListOf<Releasable>()

    protected fun retainForGC(obj: Any) {
        myGcRetained.add(obj)
        gcRoots.add(obj)
    }

    /** The root UIView of this view. */
    lateinit var rootView: UIView
        protected set

    /** What the scope is told to call on a change: kept to take back only this one. */
    val updater: () -> Unit = { update() }

    /** The views of the scopes this one holds. */
    private val children = LinkedHashSet<AbstractViewIos<*>>()

    /** The views that hold this one. */
    private val holders = HashSet<AbstractViewIos<*>>()

    /** The views asked for during the drawing under way; `null` outside one. */
    private var drawn: MutableSet<AbstractViewIos<*>>? = null

    /** Whether this view was let go of ([release]): it is never marked dirty nor drawn again. */
    var released = false
        private set

    private var firstRender = true

    val instanceId: String get() = viewId

    /** The scope changed: draw it again. */
    fun update() {
        if (released) return
        ViewUpdateScheduler.markDirty(this, app)
    }

    /** The view of a scope this one holds (a presenter's below, a panel, a row), asked for while drawing. */
    protected fun viewOf(held: Scope): AbstractViewIos<*> {
        val child = app.viewOf(held)
        child.holders += this
        children += child
        drawn?.add(child)
        return child
    }

    /**
     * Lets go of this view: the scope no longer tells it of changes, its UIView leaves the screen, and so do the views
     * it held that no other view holds.
     */
    fun release() {
        if (released) return
        released = true
        scope.clearUpdater(updater)
        app.forgetView(this)
        if (::rootView.isInitialized) rootView.removeFromSuperview()
        onRelease()
        myListSlots.forEach { it.releaseAll() }
        myListSlots.clear()
        myGcRetained.forEach { gcRoots.remove(it) }
        myGcRetained.clear()
        val held = children.toList()
        children.clear()
        for (child in held) child.dropHolder(this)
    }

    /** The view's own cleanup when it is released (observers, timers). */
    protected open fun onRelease() {}

    private fun dropHolder(holder: AbstractViewIos<*>) {
        holders -= holder
        if (holders.isEmpty()) release()
    }

    /**
     * Build the UIView hierarchy. Called once during initialization.
     * This is the "template" — creates the static structure.
     */
    abstract fun createView(): UIView

    /**
     * Reconcile the scope to UIKit views. Called at most once per frame.
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
     * Initialize the view: creates the hierarchy and draws it the first time.
     */
    fun initialize(): AbstractViewIos<S> {
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
        val asked = HashSet<AbstractViewIos<*>>()
        drawn = asked
        try {
            doUpdate()
        } catch (e: Exception) {
            NSLog("AbstractViewIos[$viewId] doUpdate error: ${e.message}")
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

    /**
     * Wrap a user-initiated action in error handling.
     * Logs and swallows exceptions to prevent crashes from UI interactions.
     */
    protected fun safeAction(context: String, action: suspend () -> Unit) {
        app.presenterScope.launch {
            try {
                action()
            } catch (e: Exception) {
                app.alertUnexpectedError(LOG, "action '$context' error", e)
            }
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
        var current: AbstractViewIos<*>? = null
            private set

        /**
         * Synchronize the slot: if newView differs from current, swap.
         * Returns true if the view changed.
         */
        fun sync(newView: AbstractViewIos<*>?): Boolean {
            if (current === newView) return false
            val oldSubviews = container.subviews.toList()
            oldSubviews.forEach { (it as? UIView)?.removeFromSuperview() }
            if (newView != null) {
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
     * Works with any container: UIStackView (addArrangedSubview) or UIView (addSubview).
     */
    protected fun <T, V : AbstractViewIos<*>> newListSlot(
        container: UIView,
        factory: () -> V,
        updater: (V, T) -> Unit
    ): ListSlot<T, V> {
        val slot = ListSlot(container, factory, updater)
        myListSlots.add(slot)
        return slot
    }

    /**
     * A list slot that recycles item views — grows/shrinks at edges, updates in-place.
     * Layout-agnostic: the container determines how children are arranged.
     */
    class ListSlot<T, V : AbstractViewIos<*>>(
        private val container: UIView,
        private val factory: () -> V,
        private val updater: (V, T) -> Unit
    ) : Releasable {
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
                if (container is UIStackView) {
                    (container as UIStackView).addArrangedSubview(view.rootView)
                } else {
                    container.addSubview(view.rootView)
                }
            }

            if (items != null) {
                for (i in 0 until newSize) {
                    updater(viewList[i], items[i])
                }
            }

            container.setNeedsLayout()
        }

        override fun releaseAll() {
            viewList.forEach { it.release() }
            viewList.clear()
        }
    }
}
