package br.com.wdc.framework.cube.remote.bridge

/**
 * The values of a scope the host sent, read by name: what a view paints. The host writes each field of the scope as
 * itself, a nested scope as `"<name>ViewId"` (the id of its view) and a list of them as `"<name>ViewIds"`; a field
 * with no value is left out.
 *
 * ```kotlin
 * val scope = bridge.useView(props.vsid)
 * val name = scope.string("nickName")
 * val panel = scope.viewId("productsPanel")
 * ```
 */
class RemoteScope internal constructor(internal val values: dynamic) {

    /** The raw value of [name] (`undefined` when absent): for shapes the readers below do not cover. */
    operator fun get(name: String): dynamic = values[name]

    fun has(name: String): Boolean = values[name] != undefined && values[name] != null

    fun string(name: String): String? = if (has(name)) values[name].toString() else null

    fun int(name: String): Int? = if (has(name)) (values[name] as Number).toInt() else null

    fun long(name: String): Long? = if (has(name)) (values[name] as Number).toLong() else null

    fun double(name: String): Double? = if (has(name)) (values[name] as Number).toDouble() else null

    fun boolean(name: String): Boolean = values[name] == true

    /** The id of the view of the scope in field [name] (sent as `"<name>ViewId"`). */
    fun viewId(name: String): String? = string("${name}ViewId")

    /** The ids of the views of the scopes in list field [name] (sent as `"<name>ViewIds"`). */
    fun viewIds(name: String): List<String> = strings("${name}ViewIds")

    fun strings(name: String): List<String> =
        if (has(name)) (values[name] as Array<*>).map { it.toString() } else emptyList()

    /** An object field (a struct the scope holds), read the same way. */
    fun obj(name: String): RemoteScope? = if (has(name)) RemoteScope(values[name]) else null

    /** A list of objects. */
    fun objects(name: String): List<RemoteScope> =
        if (has(name)) (values[name] as Array<dynamic>).map { RemoteScope(it) } else emptyList()
}
