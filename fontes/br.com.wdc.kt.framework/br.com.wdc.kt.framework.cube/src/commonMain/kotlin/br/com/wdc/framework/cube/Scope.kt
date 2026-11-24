package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * What a presenter shows: the values its view paints. It tells its presenter when it changes, so the presenter need
 * not call `update()` by hand, and it writes itself as JSON for views that receive it serialized.
 *
 * Declare each field with [field]: assigning it a value different from the current one (`!=`, so lists compare by
 * content) notifies the observer — the presenter, through `bind(...)` in [AbstractCubePresenter] and
 * [AbstractChildPresenter], which calls `update()`. Several changes in a row cost one repaint: `update()` only marks
 * the view dirty, and the view layer paints once per cycle.
 *
 * ```kotlin
 * class CartScope : Scope() {
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
abstract class Scope {

    private var observer: (() -> Unit)? = null

    /** Writes this scope as the JSON object of the view instance [instanceId]. */
    abstract fun write(instanceId: String, json: ExtensibleObjectOutput)

    /** This scope as JSON, for the view instance [instanceId]. */
    fun toJson(instanceId: String): String {
        val jsonOutput = JsonOutputFactory.createStringOutput()
        write(instanceId, jsonOutput.output)
        return jsonOutput.resultString()
    }

    /** Sets who is told of each change (one observer; `null` removes it). */
    fun observe(observer: (() -> Unit)?) {
        this.observer = observer
    }

    /** Tells the observer of a change the fields cannot see: inside an object already assigned. */
    fun changed() {
        observer?.invoke()
    }

    /** An observed field, starting at [initial]. */
    protected fun <T> field(initial: T): ReadWriteProperty<Scope, T> = Field(initial)

    private class Field<T>(private var value: T) : ReadWriteProperty<Scope, T> {
        override fun getValue(thisRef: Scope, property: KProperty<*>): T = value

        override fun setValue(thisRef: Scope, property: KProperty<*>, value: T) {
            if (this.value == value) return
            this.value = value
            thisRef.changed()
        }
    }
}
