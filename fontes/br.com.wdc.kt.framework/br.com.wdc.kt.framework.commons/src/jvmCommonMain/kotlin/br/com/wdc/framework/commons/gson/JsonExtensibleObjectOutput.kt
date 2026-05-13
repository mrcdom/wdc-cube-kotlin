package br.com.wdc.framework.commons.gson

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import com.google.gson.stream.JsonWriter
import java.io.Closeable
import java.io.Flushable
import java.util.Base64

class JsonExtensibleObjectOutput(
    private val impl: JsonWriter,
    private val useIdAsKey: Boolean = false,
) : ExtensibleObjectOutput, Closeable, Flushable {

    override fun close() {
        impl.close()
    }

    override fun flush() {
        impl.flush()
    }

    override fun beginArray(): ExtensibleObjectOutput {
        impl.beginArray()
        return this
    }

    override fun endArray(): ExtensibleObjectOutput {
        impl.endArray()
        return this
    }

    override fun beginObject(): ExtensibleObjectOutput {
        impl.beginObject()
        return this
    }

    override fun endObject(): ExtensibleObjectOutput {
        impl.endObject()
        return this
    }

    override fun name(name: String): ExtensibleObjectOutput {
        impl.name(name)
        return this
    }

    override fun name(id: Int, name: String): ExtensibleObjectOutput {
        if (useIdAsKey) {
            impl.name(id.toString())
        } else {
            impl.name(name)
        }
        return this
    }

    override fun value(value: String?): ExtensibleObjectOutput {
        impl.value(value)
        return this
    }

    override fun value(value: ByteArray?): ExtensibleObjectOutput {
        if (value == null) {
            impl.nullValue()
        } else {
            impl.value(Base64.getEncoder().encodeToString(value))
        }
        return this
    }

    override fun nullValue(): ExtensibleObjectOutput {
        impl.nullValue()
        return this
    }

    override fun value(value: Boolean): ExtensibleObjectOutput {
        impl.value(value)
        return this
    }

    override fun value(value: Double): ExtensibleObjectOutput {
        impl.value(value)
        return this
    }

    override fun value(value: Long): ExtensibleObjectOutput {
        impl.value(value)
        return this
    }

    override fun value(value: Number?): ExtensibleObjectOutput {
        impl.value(value)
        return this
    }
}
