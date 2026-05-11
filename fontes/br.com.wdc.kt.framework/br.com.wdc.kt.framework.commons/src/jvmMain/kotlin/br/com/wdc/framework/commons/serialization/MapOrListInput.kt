package br.com.wdc.framework.commons.serialization

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.framework.commons.lang.CoerceUtilsJvm
import java.io.IOException

class MapOrListInput : ExtensibleObjectInput {

    private var current: StackItem

    constructor(map: Map<*, *>) {
        current = StackItem().apply {
            name = null
            value = map
            token = SerializationToken.BEGIN_OBJECT
        }
    }

    constructor(list: List<*>) {
        current = StackItem().apply {
            name = null
            value = list
            token = SerializationToken.BEGIN_ARRAY
        }
    }

    override fun beginObject() {
        if (current.token != SerializationToken.BEGIN_OBJECT) {
            throw IOException("Expected BEGIN_OBJECT but found ${current.token.name}")
        }
        current.token = SerializationToken.END_OBJECT

        val valueMap = current.value as? Map<*, *>
            ?: throw IOException("Expected value as a Map object")

        current = StackItem().apply {
            previous = this@MapOrListInput.current
            it = valueMap.entries.iterator()
            hasValue = false
        }
        fetchNext()
    }

    override fun endObject() {
        val previousStackItem = current.previous
            ?: throw IOException("Expected an element but no one was found")

        if (previousStackItem.token != SerializationToken.END_OBJECT) {
            throw IOException("Expected END_OBJECT but found ${current.token.name}")
        }

        current = previousStackItem
        if (current.previous != null) {
            fetchNext()
        }
    }

    override fun beginArray() {
        if (current.token != SerializationToken.BEGIN_ARRAY) {
            throw IOException("Expected BEGIN_ARRAY but found ${current.token.name}")
        }
        current.token = SerializationToken.END_ARRAY

        val valueList = current.value as? List<*>
            ?: throw IOException("Expected value as a List object")

        current = StackItem().apply {
            previous = this@MapOrListInput.current
            it = valueList.iterator()
            hasValue = false
        }
        fetchNext()
    }

    override fun endArray() {
        val previousStackItem = current.previous
            ?: throw IOException("Expected an element but no one was found")

        if (previousStackItem.token != SerializationToken.END_ARRAY) {
            throw IOException("Expected END_ARRAY but found ${current.token.name}")
        }

        current = previousStackItem
        if (current.previous != null) {
            fetchNext()
        }
    }

    private fun fetchNext() {
        val iterator = current.it
            ?: throw IOException("Expected an iterator but found ${current.value}")

        if (!iterator.hasNext()) {
            current.name = null
            current.value = null
            current.hasValue = false
            current.previous?.let { current.token = it.token }
            return
        }

        var item: Any? = iterator.next()
        if (item is Map.Entry<*, *>) {
            current.name = CoerceUtils.asString(item.key)
            item = item.value
        }

        current.value = item
        current.token = resolveToken(item)
        current.hasValue = true
    }

    override fun hasNext(): Boolean = current.hasValue

    override fun peek(): SerializationToken = current.token

    override fun nextName(): String = current.name ?: ""

    override fun <T> nextNull(): T? {
        if (current.value != null) {
            throw IOException("Expected null value but found ${current.value}")
        }
        fetchNext()
        return null
    }

    override fun nextString(): String {
        val result = CoerceUtils.asString(current.value)
        fetchNext()
        return result ?: ""
    }

    override fun nextBoolean(): Boolean {
        val result = CoerceUtils.asBoolean(current.value, false)!!
        fetchNext()
        return result
    }

    override fun nextNumber(): Number? {
        val result = CoerceUtilsJvm.asNumber(current.value)
        fetchNext()
        return result
    }

    override fun nextDouble(): Double {
        val result = CoerceUtils.asDouble(current.value, 0.0)!!
        fetchNext()
        return result
    }

    override fun nextLong(): Long {
        val result = CoerceUtils.asLong(current.value, 0L)!!
        fetchNext()
        return result
    }

    override fun nextInt(): Int {
        val result = CoerceUtils.asInteger(current.value, 0)!!
        fetchNext()
        return result
    }

    override fun skipValue() {
        current.value = null
        fetchNext()
    }

    private class StackItem {
        var previous: StackItem? = null
        var token: SerializationToken = SerializationToken.END_DOCUMENT
        var name: String? = null
        var value: Any? = null
        var it: Iterator<*>? = null
        var hasValue: Boolean = false
    }

    companion object {
        private fun resolveToken(value: Any?): SerializationToken = when (value) {
            null -> SerializationToken.NULL
            is Map<*, *> -> SerializationToken.BEGIN_OBJECT
            is List<*> -> SerializationToken.BEGIN_ARRAY
            is String, is Char -> SerializationToken.STRING
            is Number -> SerializationToken.NUMBER
            is Boolean -> SerializationToken.BOOLEAN
            else -> throw IOException("Non supported value: $value")
        }
    }
}
