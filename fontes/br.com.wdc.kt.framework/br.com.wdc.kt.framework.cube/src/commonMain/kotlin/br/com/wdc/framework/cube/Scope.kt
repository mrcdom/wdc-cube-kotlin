package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.serialization.Externalizable
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * What a presenter shows: the values its view paints. It tells whoever subscribed ([subscribe]) when it changes, so
 * the presenter need not ask for a repaint, and it writes itself as JSON for views that receive it serialized
 * ([write]).
 *
 * Declare each field with [field]: assigning it a value different from the current one (`!=`, so lists compare by
 * content) is a change. Several changes in a row cost one repaint: the view layer collects them and paints once per
 * cycle.
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
 * Changes made in `commitComputedState()` count too: the view layer runs it before it picks the views to paint, so
 * they are painted in the same cycle.
 *
 * **Lifecycle:** the view layer owns the views. A view that draws this scope subscribes to it ([subscribe]) and
 * unsubscribes when it leaves the screen; a layer that keeps view objects drops them when the scope is released
 * ([onRelease]). The presenter releases its scope and its segments ([release]) when it is released, and a segment
 * when it leaves the screen.
 *
 * **As JSON** ([write]), the fields go out in declaration order, after `"id"` (the view instance): `null` and blank
 * strings are left out; numbers and booleans always go; a [Scope] (a presenter's below, a segment) goes as
 * `"<name>ViewId"` with the instance id of the view that draws it (the `viewOf` the layer passes), and a list declared
 * with [scopes] as `"<name>ViewIds"`; a [CubeView] goes as `"<name>Id"`; lists, maps, enums, [ScopeValue] and
 * [Externalizable] values are written as themselves. Plain `var`s (the actions) never go. A field declared with
 * `sent = false` never goes (a password the user types, for example). Override [write] only for a shape these
 * rules can't give.
 */
@OptIn(ExperimentalAtomicApi::class)
abstract class Scope {

    private val listeners = AtomicReference<List<Listener>>(emptyList())

    private val releaseListeners = AtomicReference<List<Listener>>(emptyList())

    private val changes = AtomicInt(0)

    private val releasedFlag = AtomicInt(0)

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
            if (!field.sent) continue
            if (field.scopes) writeScopes(json, field.name, field.value as List<*>, viewOf)
            else writeField(json, field.name, field.value, viewOf)
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

    /**
     * Calls [listener] after each change, on the thread that made it, until the returned function is called. Many
     * may subscribe (a view on screen, a layer that keeps view objects).
     */
    fun subscribe(listener: () -> Unit): () -> Unit = add(listeners, listener)

    /**
     * How many changes this scope has had: a view that read it while painting and subscribes afterwards compares it
     * to see whether it missed one in between.
     */
    val version: Int get() = changes.load()

    /** Tells the subscribers of a change the fields cannot see: inside an object already assigned. */
    fun changed() {
        changes.incrementAndFetch()
        for (listener in listeners.load()) listener.fn()
    }

    // :: Release

    /** Whether [release] was called. */
    val released: Boolean get() = releasedFlag.load() != 0

    /**
     * Calls [listener] once, when the scope is released (a layer that keeps a view of it drops the view), unless the
     * returned function is called before.
     */
    fun onRelease(listener: () -> Unit): () -> Unit = add(releaseListeners, listener)

    /**
     * The scope left the screen for good: tells who registered with [onRelease] and drops every subscriber. Called by
     * the presenter for its scope and its segments; a second call does nothing.
     */
    fun release() {
        if (!releasedFlag.compareAndSet(0, 1)) return
        val toCall = releaseListeners.exchange(emptyList())
        listeners.store(emptyList())
        for (listener in toCall) listener.fn()
    }

    /**
     * An observed field, starting at [initial]. With [sent] `false`, the field stays out of the JSON a remote view
     * receives.
     */
    protected fun <T> field(initial: T, sent: Boolean = true): PropertyDelegateProvider<Scope, ReadWriteProperty<Scope, T>> =
        PropertyDelegateProvider { scope, property -> Field(property.name, initial, sent).also { scope.fields += it } }

    /**
     * An observed list of scopes (the rows of a list, each one a view of its own): goes out as `"<name>ViewIds"`,
     * empty included. Replace the list to change it; the presenter reuses the rows that stay (`segment`) and releases
     * the ones that leave (`detach`).
     */
    protected fun <S : Scope> scopes(): PropertyDelegateProvider<Scope, ReadWriteProperty<Scope, List<S>>> =
        PropertyDelegateProvider { scope, property ->
            Field<List<S>>(property.name, emptyList(), sent = true, scopes = true).also { scope.fields += it }
        }

    private class Field<T>(val name: String, var value: T, val sent: Boolean, val scopes: Boolean = false) :
        ReadWriteProperty<Scope, T> {
        override fun getValue(thisRef: Scope, property: KProperty<*>): T = value

        override fun setValue(thisRef: Scope, property: KProperty<*>, value: T) {
            if (this.value == value) return
            this.value = value
            thisRef.changed()
        }
    }

    /** A wrapper, so the same function subscribed twice is two subscriptions. */
    private class Listener(val fn: () -> Unit)

    private companion object {

        fun add(list: AtomicReference<List<Listener>>, fn: () -> Unit): () -> Unit {
            val listener = Listener(fn)
            while (true) {
                val current = list.load()
                if (list.compareAndSet(current, current + listener)) break
            }
            return {
                while (true) {
                    val current = list.load()
                    if (listener !in current || list.compareAndSet(current, current - listener)) break
                }
            }
        }

        fun writeScopes(json: ExtensibleObjectOutput, name: String, rows: List<*>, viewOf: ((Scope) -> CubeView)?) {
            if (viewOf == null) return
            json.name(name + "ViewIds").beginArray()
            for (row in rows) json.value(viewOf(row as Scope).instanceId)
            json.endArray()
        }

        fun writeField(json: ExtensibleObjectOutput, name: String, value: Any?, viewOf: ((Scope) -> CubeView)?) {
            when {
                value == null -> {}
                value is String -> if (value.isNotBlank()) json.name(name).value(value)
                value is Scope -> if (viewOf != null) json.name(name + "ViewId").value(viewOf(value).instanceId)
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
