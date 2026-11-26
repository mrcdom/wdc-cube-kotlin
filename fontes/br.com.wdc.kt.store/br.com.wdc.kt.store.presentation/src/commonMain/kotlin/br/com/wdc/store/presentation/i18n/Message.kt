package br.com.wdc.store.presentation.i18n

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.ScopeValue

/**
 * A text a view shows, by its key in the catalog (`:store-i18n`, `MessageKeys`) and the values of its placeholders.
 * The presentation does not know the user's language: each view translates the message with its own `Messages`
 * (on the remote views, the browser does; the message goes as `{"key": …, "args": {…}}`).
 */
class Message(val key: String, val args: Map<String, String> = emptyMap()) : ScopeValue {

    constructor(key: String, vararg args: Pair<String, Any?>) : this(key, args.associate { (name, value) -> name to value.toString() })

    override fun writeJson(json: ExtensibleObjectOutput) {
        json.beginObject()
        json.name("key").value(key)
        json.name("args").beginObject()
        for ((name, value) in args) json.name(name).value(value)
        json.endObject()
        json.endObject()
    }

    override fun equals(other: Any?): Boolean = other is Message && other.key == key && other.args == args

    override fun hashCode(): Int = key.hashCode() * 31 + args.hashCode()

    override fun toString(): String = if (args.isEmpty()) key else "$key $args"
}
