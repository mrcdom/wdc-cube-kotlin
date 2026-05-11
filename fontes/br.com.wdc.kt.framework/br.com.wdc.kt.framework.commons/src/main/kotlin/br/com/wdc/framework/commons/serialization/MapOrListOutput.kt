package br.com.wdc.framework.commons.serialization

class MapOrListOutput : ExtensibleObjectOutput {

    private var current: StackItem<*>

    constructor() {
        val item = StackItem<Any?>()
        item.add = { value -> item.value = value }
        current = item
    }

    constructor(map: MutableMap<String, Any?>) {
        val item = StackItem<MutableMap<String, Any?>>()
        item.value = map
        item.add = newAddOnMap(item, map)
        current = item
    }

    constructor(list: MutableList<Any?>) {
        val item = StackItem<MutableList<Any?>>()
        item.value = list
        item.add = newAddOnList(list)
        current = item
    }

    val value: Any? get() = current.value

    override fun beginArray(): ExtensibleObjectOutput {
        val item = StackItem<MutableList<Any?>>()
        item.previous = current
        item.value = ArrayList()
        item.add = newAddOnList(item.value!!)
        current = item
        return this
    }

    override fun endArray(): ExtensibleObjectOutput {
        current.previous!!.add(current.value)
        current = current.previous!!
        return this
    }

    override fun beginObject(): ExtensibleObjectOutput {
        val item = StackItem<MutableMap<String, Any?>>()
        item.previous = current
        item.value = LinkedHashMap()
        item.add = newAddOnMap(item, item.value!!)
        current = item
        return this
    }

    override fun endObject(): ExtensibleObjectOutput {
        current.previous!!.add(current.value)
        current = current.previous!!
        return this
    }

    override fun name(name: String): ExtensibleObjectOutput {
        current.name = name
        return this
    }

    override fun name(id: Int, name: String): ExtensibleObjectOutput {
        current.name = if (name.isBlank()) id.toString() else name
        return this
    }

    override fun value(value: String?): ExtensibleObjectOutput {
        current.add(value)
        return this
    }

    override fun value(value: ByteArray?): ExtensibleObjectOutput {
        current.add(value)
        return this
    }

    override fun nullValue(): ExtensibleObjectOutput {
        current.add(null)
        return this
    }

    override fun value(value: Boolean): ExtensibleObjectOutput {
        current.add(value)
        return this
    }

    override fun value(value: Double): ExtensibleObjectOutput {
        current.add(value)
        return this
    }

    override fun value(value: Long): ExtensibleObjectOutput {
        current.add(value)
        return this
    }

    override fun value(value: Number?): ExtensibleObjectOutput {
        current.add(value)
        return this
    }

    private class StackItem<T> {
        var previous: StackItem<*>? = null
        var name: String? = null
        var value: T? = null
        var add: (Any?) -> Unit = {}
    }

    private companion object {
        fun newAddOnList(list: MutableList<Any?>): (Any?) -> Unit = { list.add(it) }

        fun <T> newAddOnMap(current: StackItem<T>, map: MutableMap<String, Any?>): (Any?) -> Unit =
            { obj -> map[current.name ?: ""] = obj }
    }
}
