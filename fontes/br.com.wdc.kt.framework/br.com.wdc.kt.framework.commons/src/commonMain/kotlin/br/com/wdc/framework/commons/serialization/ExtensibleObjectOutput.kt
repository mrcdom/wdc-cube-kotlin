package br.com.wdc.framework.commons.serialization

interface ExtensibleObjectOutput {

    fun beginArray(): ExtensibleObjectOutput
    fun endArray(): ExtensibleObjectOutput

    fun beginObject(): ExtensibleObjectOutput
    fun endObject(): ExtensibleObjectOutput

    fun name(name: String): ExtensibleObjectOutput
    fun name(id: Int, name: String): ExtensibleObjectOutput

    fun value(value: String?): ExtensibleObjectOutput
    fun value(value: ByteArray?): ExtensibleObjectOutput
    fun nullValue(): ExtensibleObjectOutput
    fun value(value: Boolean): ExtensibleObjectOutput
    fun value(value: Double): ExtensibleObjectOutput
    fun value(value: Long): ExtensibleObjectOutput
    fun value(value: Number?): ExtensibleObjectOutput
}
