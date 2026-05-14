package br.com.wdc.framework.commons.serialization

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Streaming JSON writer — pure Kotlin, sem dependências de plataforma.
 *
 * Implementa [ExtensibleObjectOutput] escrevendo diretamente num StringBuilder
 * sem construir árvore intermediária (Map/List).
 */
class JsonStreamWriter : ExtensibleObjectOutput {

    private val sb = StringBuilder()
    private var scopeStack = IntArray(16)
    private var stackSize = 0
    private var pendingName: String? = null

    private companion object {
        private const val SCOPE_EMPTY_OBJECT = 0
        private const val SCOPE_NONEMPTY_OBJECT = 1
        private const val SCOPE_EMPTY_ARRAY = 2
        private const val SCOPE_NONEMPTY_ARRAY = 3
    }

    fun result(): String = sb.toString()

    override fun beginObject(): ExtensibleObjectOutput {
        beforeValue()
        sb.append('{')
        push(SCOPE_EMPTY_OBJECT)
        return this
    }

    override fun endObject(): ExtensibleObjectOutput {
        stackSize--
        sb.append('}')
        return this
    }

    override fun beginArray(): ExtensibleObjectOutput {
        beforeValue()
        sb.append('[')
        push(SCOPE_EMPTY_ARRAY)
        return this
    }

    override fun endArray(): ExtensibleObjectOutput {
        stackSize--
        sb.append(']')
        return this
    }

    override fun name(name: String): ExtensibleObjectOutput {
        pendingName = name
        return this
    }

    override fun name(id: Int, name: String): ExtensibleObjectOutput {
        pendingName = if (name.isBlank()) id.toString() else name
        return this
    }

    override fun value(value: String?): ExtensibleObjectOutput {
        if (value == null) return nullValue()
        beforeValue()
        writeString(value)
        return this
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun value(value: ByteArray?): ExtensibleObjectOutput {
        if (value == null) return nullValue()
        beforeValue()
        writeString(Base64.encode(value))
        return this
    }

    override fun nullValue(): ExtensibleObjectOutput {
        beforeValue()
        sb.append("null")
        return this
    }

    override fun value(value: Boolean): ExtensibleObjectOutput {
        beforeValue()
        sb.append(if (value) "true" else "false")
        return this
    }

    override fun value(value: Double): ExtensibleObjectOutput {
        beforeValue()
        sb.append(value)
        return this
    }

    override fun value(value: Long): ExtensibleObjectOutput {
        beforeValue()
        sb.append(value)
        return this
    }

    override fun value(value: Number?): ExtensibleObjectOutput {
        if (value == null) return nullValue()
        beforeValue()
        sb.append(value)
        return this
    }

    // ── Internal ──

    private fun beforeValue() {
        if (stackSize > 0) {
            val scope = scopeStack[stackSize - 1]
            when (scope) {
                SCOPE_EMPTY_OBJECT -> {
                    scopeStack[stackSize - 1] = SCOPE_NONEMPTY_OBJECT
                    writePendingName()
                }
                SCOPE_NONEMPTY_OBJECT -> {
                    sb.append(',')
                    writePendingName()
                }
                SCOPE_EMPTY_ARRAY -> {
                    scopeStack[stackSize - 1] = SCOPE_NONEMPTY_ARRAY
                }
                SCOPE_NONEMPTY_ARRAY -> {
                    sb.append(',')
                }
            }
        }
    }

    private fun writePendingName() {
        val name = pendingName
            ?: throw IllegalStateException("value() called without name() in object")
        pendingName = null
        writeString(name)
        sb.append(':')
    }

    private fun writeString(value: String) {
        sb.append('"')
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code < 0x20) {
                        sb.append("\\u")
                        sb.append(c.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('"')
    }

    private fun push(scope: Int) {
        if (stackSize >= scopeStack.size) {
            scopeStack = scopeStack.copyOf(scopeStack.size * 2)
        }
        scopeStack[stackSize++] = scope
    }
}
