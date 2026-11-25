package br.com.wdc.framework.cube.remote.bridge

/**
 * One view the host published, by its instance id (`"<vid>:<n>"`): the values of its scope, as they last came, and
 * the views it refers to (its `…ViewId`/`…ViewIds` fields), which the collector keeps while it does.
 */
class ViewBinding internal constructor(val vsid: String) {

    internal val values: dynamic = js("({})")

    internal var referencedViewIds: Set<String> = emptySet()
        private set

    /** Told when the values change: the view repaints. One listener, the view on screen. */
    var onChange: () -> Unit = {}

    /** The scope as it is now. */
    val scope: RemoteScope get() = RemoteScope(values)

    /** Replaces the values with [newValues] (a scope from the host) and repaints. */
    internal fun setScope(newValues: dynamic) {
        referencedViewIds = viewIdRefs(newValues)
        val keys = js("Object.keys")(values).unsafeCast<Array<String>>()
        for (key in keys) js("Reflect.deleteProperty")(values, key)
        js("Object.assign")(values, newValues)
        onChange()
    }

    /** Sets one value on the client's side (the browser's `submitting` or `error`) and repaints. */
    internal fun set(name: String, value: Any?) {
        values[name] = value
        onChange()
    }

    private fun viewIdRefs(scope: dynamic): Set<String> {
        val refs = HashSet<String>()
        val keys = js("Object.keys")(scope).unsafeCast<Array<String>>()
        for (key in keys) {
            val value = scope[key]
            if (key.endsWith("ViewId") && jsTypeOf(value) == "string" && (value as String).isNotEmpty()) refs.add(value as String)
            if (key.endsWith("ViewIds") && value != null && js("Array.isArray")(value) == true) {
                for (item in value.unsafeCast<Array<Any?>>()) if (item is String && item.isNotEmpty()) refs += item
            }
        }
        return refs
    }
}
