package br.com.wdc.framework.commons.gson

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.SerializationToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.Closeable

class JsonExtensibleObjectInput(private val impl: JsonReader) : ExtensibleObjectInput, Closeable {

    override fun close() {
        impl.close()
    }

    override fun beginArray() {
        impl.beginArray()
    }

    override fun endArray() {
        impl.endArray()
    }

    override fun beginObject() {
        impl.beginObject()
    }

    override fun endObject() {
        impl.endObject()
    }

    override fun hasNext(): Boolean = impl.hasNext()

    override fun peek(): SerializationToken = toSerializationToken(impl.peek())

    override fun nextName(): String = impl.nextName()

    override fun nextString(): String = impl.nextString()

    override fun nextBoolean(): Boolean = impl.nextBoolean()

    override fun <T> nextNull(): T? {
        impl.nextNull()
        return null
    }

    override fun nextDouble(): Double = impl.nextDouble()

    override fun nextNumber(): Number? {
        val token = impl.peek()
        if (token == JsonToken.NULL) {
            impl.nextNull()
            return null
        }
        val str = impl.nextString()
        if ('.' in str || 'e' in str || 'E' in str) {
            return str.toDouble()
        }
        val longVal = str.toLong()
        return if (longVal in Int.MIN_VALUE..Int.MAX_VALUE) longVal.toInt() else longVal
    }

    override fun nextLong(): Long = impl.nextLong()

    override fun nextInt(): Int = impl.nextInt()

    override fun skipValue() {
        impl.skipValue()
    }

    private companion object {
        fun toSerializationToken(token: JsonToken): SerializationToken = when (token) {
            JsonToken.BEGIN_ARRAY -> SerializationToken.BEGIN_ARRAY
            JsonToken.END_ARRAY -> SerializationToken.END_ARRAY
            JsonToken.BEGIN_OBJECT -> SerializationToken.BEGIN_OBJECT
            JsonToken.END_OBJECT -> SerializationToken.END_OBJECT
            JsonToken.NAME -> SerializationToken.NAME
            JsonToken.STRING -> SerializationToken.STRING
            JsonToken.NUMBER -> SerializationToken.NUMBER
            JsonToken.BOOLEAN -> SerializationToken.BOOLEAN
            JsonToken.NULL -> SerializationToken.NULL
            JsonToken.END_DOCUMENT -> SerializationToken.END_DOCUMENT
        }
    }
}
