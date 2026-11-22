package br.com.wdc.framework.cube

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A [ViewState] that tells its presenter when it changes, so the presenter need not call `update()` by hand.
 *
 * Declare each field with [field]: assigning it a value different from the current one (`!=`, so lists compare by
 * content) notifies the observer — the presenter, through `observed(...)` in [AbstractCubePresenter] and
 * [AbstractChildPresenter], which calls `update()`. Several changes in a row cost one repaint: `update()` only marks
 * the view dirty, and the view layer paints once per cycle.
 *
 * ```kotlin
 * class CartViewState : ObservableViewState() {
 *     var items: List<CartItem> by field(emptyList())
 *     var errorMessage: String? by field(null)
 * }
 * ```
 *
 * **What the fields cannot see:** a change *inside* an object already assigned (an item added to a mutable list, a
 * field of a nested object). Assign a new value instead, or call [changed]. A plain `var` (not declared with [field])
 * is not observed either.
 *
 * Changes made in `commitComputedState()` notify too, but the view being painted ignores its own `update()` then.
 */
abstract class ObservableViewState : ViewState {

    private var observer: (() -> Unit)? = null

    /** Sets who is told of each change (one observer; `null` removes it). */
    fun observe(observer: (() -> Unit)?) {
        this.observer = observer
    }

    /** Tells the observer of a change the fields cannot see: inside an object already assigned. */
    fun changed() {
        observer?.invoke()
    }

    /** An observed field, starting at [initial]. */
    protected fun <T> field(initial: T): ReadWriteProperty<ObservableViewState, T> = Field(initial)

    private class Field<T>(private var value: T) : ReadWriteProperty<ObservableViewState, T> {
        override fun getValue(thisRef: ObservableViewState, property: KProperty<*>): T = value

        override fun setValue(thisRef: ObservableViewState, property: KProperty<*>, value: T) {
            if (this.value == value) return
            this.value = value
            thisRef.changed()
        }
    }
}
