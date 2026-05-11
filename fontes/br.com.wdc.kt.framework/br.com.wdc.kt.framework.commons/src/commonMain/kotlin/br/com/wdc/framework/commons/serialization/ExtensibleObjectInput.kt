package br.com.wdc.framework.commons.serialization

interface ExtensibleObjectInput {

    fun beginArray()
    fun endArray()
    fun beginObject()
    fun endObject()

    fun hasNext(): Boolean
    fun peek(): SerializationToken

    fun nextName(): String
    fun nextString(): String
    fun nextBoolean(): Boolean
    fun <T> nextNull(): T?
    fun nextDouble(): Double
    fun nextNumber(): Number?
    fun nextLong(): Long
    fun nextInt(): Int

    fun skipValue()
}
