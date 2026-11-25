package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.serialization.Externalizable
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * What a presenter shows: the values its view paints, and the actions the view calls. The framework knows nothing
 * else of views: drawing a scope, and letting go of what drew it, is up to each rendering technology.
 *
 * Declare each field with [field]: assigning it a value different from the current one (`!=`, so lists compare by
 * content) is a change, and a change tells the updater ([setUpdater]) — so the presenter need not ask for a repaint.
 * Several changes in a row cost one repaint: the technology collects them and paints once per cycle.
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
 * Changes made in `commitComputedState()` count too: the technology runs it before it picks the views to paint, so
 * they are painted in the same cycle.
 *
 * **Scopes inside scopes:** a scope held in a field (a presenter's below, a panel) or in a list declared with
 * [scopes] (the rows) is drawn by a view of its own. A scope leaves the screen when it leaves the field of its parent:
 * that change repaints the parent's view, and the technology lets go of the child's view there. The presenter only
 * takes the scope out of the field.
 *
 * **As JSON** ([write]), the fields go out in declaration order, after `"id"` (the view instance): `null` and blank
 * strings are left out; numbers and booleans always go; a [Scope] goes as `"<name>ViewId"` with the instance id of the
 * view that draws it (the `viewIdOf` the technology passes), and a list declared with [scopes] as `"<name>ViewIds"`;
 * lists, maps, enums, [ScopeValue] and [Externalizable] values are written as themselves. Plain `var`s (the actions)
 * never go. A field declared with `sent = false` never goes (a password the user types, for example). Override [write]
 * only for a shape these rules can't give.
 */
@OptIn(ExperimentalAtomicApi::class)
abstract class Scope {

    private val updater = AtomicReference<(() -> Unit)?>(null)

    private val fields = mutableListOf<Field<*>>()

    private val events = mutableMapOf<Int, suspend (Map<String, Any?>) -> Unit>()

    private var syncScope: ((Map<String, Any?>) -> Unit)? = null

    /**
     * Writes this scope as the JSON object of the view instance [instanceId]: see the rules above. [viewIdOf] gives the
     * instance id of the view of a scope held in a field (without it, such a field is left out).
     */
    open fun write(instanceId: String, json: ExtensibleObjectOutput, viewIdOf: ((Scope) -> String)? = null) {
        json.beginObject()
        json.name("id").value(instanceId)
        for (field in fields) {
            if (!field.sent) continue
            if (field.scopes) writeScopes(json, field.name, field.value as List<*>, viewIdOf)
            else writeField(json, field.name, field.value, viewIdOf)
        }
        json.endObject()
    }

    /** This scope as JSON, for the view instance [instanceId]. */
    fun toJson(instanceId: String, viewIdOf: ((Scope) -> String)? = null): String {
        val jsonOutput = JsonOutputFactory.createStringOutput()
        write(instanceId, jsonOutput.output, viewIdOf)
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

    // :: Who is told of a change

    /**
     * Who is told when a field changes: what draws this scope. The technology installs it when it starts drawing the
     * scope and takes it back ([clearUpdater]) when it stops; `null` tells no one.
     */
    fun setUpdater(updater: (() -> Unit)?) {
        this.updater.store(updater)
    }

    /**
     * Takes the updater back, if it is still the one given (the same instance): what drew this scope no longer does.
     * A view that stopped drawing the scope does not take the updater of a view that started.
     */
    fun clearUpdater(updater: () -> Unit) {
        this.updater.compareAndSet(updater, null)
    }

    /** Asks for a repaint: for a change the fields cannot see, inside an object already assigned. */
    fun changed() {
        updater.load()?.invoke()
    }

    /**
     * An observed field, starting at [initial]. With [sent] `false`, the field stays out of the JSON a remote view
     * receives.
     */
    protected fun <T> field(initial: T, sent: Boolean = true): PropertyDelegateProvider<Scope, ReadWriteProperty<Scope, T>> =
        PropertyDelegateProvider { scope, property -> Field(property.name, initial, sent).also { scope.fields += it } }

    /**
     * An observed list of scopes (the rows of a list, each one a view of its own): goes out as `"<name>ViewIds"`,
     * empty included. Replace the list to change it; the presenter reuses the rows that stay, by identity, and a row
     * that leaves the list leaves the screen.
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

    private companion object {

        fun writeScopes(json: ExtensibleObjectOutput, name: String, rows: List<*>, viewIdOf: ((Scope) -> String)?) {
            if (viewIdOf == null) return
            json.name(name + "ViewIds").beginArray()
            for (row in rows) json.value(viewIdOf(row as Scope))
            json.endArray()
        }

        fun writeField(json: ExtensibleObjectOutput, name: String, value: Any?, viewIdOf: ((Scope) -> String)?) {
            when {
                value == null -> {}
                value is String -> if (value.isNotBlank()) json.name(name).value(value)
                value is Scope -> if (viewIdOf != null) json.name(name + "ViewId").value(viewIdOf(value))
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
