package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.serialization.Externalizable
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * What a presenter shows: the values its view paints. It tells its presenter when it changes, so the presenter need
 * not call `update()` by hand, and it writes itself as JSON for views that receive it serialized ([write]).
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
 * Changes made in `commitComputedState()` notify too: the view layer runs it before it picks the views to paint, so
 * they are painted in the same cycle.
 *
 * **As JSON** ([write]), the fields go out in declaration order, after `"id"` (the view instance): `null` and blank
 * strings are left out; numbers and booleans always go; a [Scope] (a presenter's below, a segment) goes as
 * `"<name>ViewId"` with the instance id of the view that draws it ([CubeApplication.viewOf]), and a list of scopes as
 * `"<name>ViewIds"`; a [CubeView] goes as `"<name>Id"`; lists, maps, enums, [ScopeValue] and [Externalizable] values
 * are written as themselves. Plain `var`s (the actions) never go. A field declared with
 * `sent = false` never goes (a password the user types, for example). Override [write] only for a shape these
 * rules can't give.
 */
abstract class Scope {

    private var observer: (() -> Unit)? = null

    private val fields = mutableListOf<Field<*>>()

    private val events = mutableMapOf<Int, suspend (Map<String, Any?>) -> Unit>()

    private var syncScope: ((Map<String, Any?>) -> Unit)? = null

    /**
     * Writes this scope as the JSON object of the view instance [instanceId]: see the rules above. [viewOf] gives the
     * view of a scope held in a field (without it, such a field is left out).
     */
    open fun write(instanceId: String, json: ExtensibleObjectOutput, viewOf: ((Scope) -> CubeView)? = null) {
        json.beginObject()
        json.name("id").value(instanceId)
        for (field in fields) {
            if (field.sent) writeField(json, field.name, field.value, viewOf)
        }
        json.endObject()
    }

    /** This scope as JSON, for the view instance [instanceId]. */
    fun toJson(instanceId: String, viewOf: ((Scope) -> CubeView)? = null): String {
        val jsonOutput = JsonOutputFactory.createStringOutput()
        write(instanceId, jsonOutput.output, viewOf)
        return jsonOutput.resultString()
    }

    // :: Remote views

    /** Keeps the handler of an event code (the presenter binds it with `action(code) { … }`). */
    internal fun onEvent(eventCode: Int, handler: suspend (Map<String, Any?>) -> Unit) {
        check(eventCode !in events) { "${this::class.simpleName}: event code $eventCode is bound twice" }
        events[eventCode] = handler
    }

    /** Runs the action bound to [eventCode], reading its values from [formData]. An unknown code is an error. */
    suspend fun dispatch(eventCode: Int, formData: Map<String, Any?>) {
        val handler = events[eventCode]
            ?: throw IllegalArgumentException("${this::class.simpleName}: no action for event code $eventCode")
        handler(formData)
    }

    /** What to copy into the scope from the form data a remote view sends before each event (what the user typed). */
    fun onSync(syncScope: (Map<String, Any?>) -> Unit) {
        this.syncScope = syncScope
    }

    /** Copies the form data a remote view sent into the scope ([onSync]). */
    fun sync(formData: Map<String, Any?>) {
        syncScope?.invoke(formData)
    }

    // :: Observation

    /** Sets who is told of each change (one observer; `null` removes it). */
    fun observe(observer: (() -> Unit)?) {
        this.observer = observer
    }

    /** Tells the observer of a change the fields cannot see: inside an object already assigned. */
    fun changed() {
        observer?.invoke()
    }

    /**
     * An observed field, starting at [initial]. With [sent] `false`, the field stays out of the JSON a remote view
     * receives.
     */
    protected fun <T> field(initial: T, sent: Boolean = true): PropertyDelegateProvider<Scope, ReadWriteProperty<Scope, T>> =
        PropertyDelegateProvider { scope, property -> Field(property.name, initial, sent).also { scope.fields += it } }

    private class Field<T>(val name: String, var value: T, val sent: Boolean) : ReadWriteProperty<Scope, T> {
        override fun getValue(thisRef: Scope, property: KProperty<*>): T = value

        override fun setValue(thisRef: Scope, property: KProperty<*>, value: T) {
            if (this.value == value) return
            this.value = value
            thisRef.changed()
        }
    }

    private companion object {

        fun writeField(json: ExtensibleObjectOutput, name: String, value: Any?, viewOf: ((Scope) -> CubeView)?) {
            when {
                value == null -> {}
                value is String -> if (value.isNotBlank()) json.name(name).value(value)
                value is Scope -> if (viewOf != null) json.name(name + "ViewId").value(viewOf(value).instanceId)
                value is List<*> && value.isNotEmpty() && value.all { it is Scope } -> if (viewOf != null) {
                    json.name(name + "ViewIds").beginArray()
                    for (item in value) json.value(viewOf(item as Scope).instanceId)
                    json.endArray()
                }
                value is CubeView -> json.name(name + "Id").value(value.instanceId)
                else -> {
                    json.name(name)
                    writeValue(json, name, value)
                }
            }
        }

        fun writeValue(json: ExtensibleObjectOutput, name: String, value: Any?) {
            when (value) {
                null -> json.nullValue()
                is String -> json.value(value)
                is Boolean -> json.value(value)
                // fractions first: in Kotlin/JS every number also passes `is Short`/`is Byte`
                is Double -> json.value(value)
                is Float -> json.value(value.toDouble())
                is Int -> json.value(value.toLong())
                is Long -> json.value(value)
                is Short -> json.value(value.toLong())
                is Byte -> json.value(value.toLong())
                is Enum<*> -> json.value(value.name)
                is ScopeValue -> value.writeJson(json)
                is Externalizable -> value.writeExternal(json)
                is CubeView -> json.value(value.instanceId)
                is Iterable<*> -> {
                    json.beginArray()
                    for (item in value) writeValue(json, name, item)
                    json.endArray()
                }
                is Map<*, *> -> {
                    json.beginObject()
                    for ((key, item) in value) {
                        json.name(key.toString())
                        writeValue(json, name, item)
                    }
                    json.endObject()
                }
                else -> throw IllegalStateException(
                    "Scope field '$name': a ${value::class.simpleName} can't be written as JSON; make it a ScopeValue"
                )
            }
        }
    }
}
