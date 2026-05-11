package br.com.wdc.framework.cube.util

import br.com.wdc.framework.cube.CubeIntent
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.Charset
import java.util.logging.Level
import java.util.logging.Logger

object QueryStringParser {

    private val logger = Logger.getLogger(QueryStringParser::class.java.name)

    private val NUMERIC_PARSERS: Map<Class<*>, (String) -> Any?> = mapOf(
        BigDecimal::class.java to { s -> BigDecimal(s) },
        Double::class.javaObjectType to { s -> s.toDouble() },
        Float::class.javaObjectType to { s -> s.toFloat() },
        BigInteger::class.java to { s -> BigInteger(s) },
        Long::class.javaObjectType to { s -> s.toLong() },
        Int::class.javaObjectType to { s -> s.toInt() },
        Short::class.javaObjectType to { s -> s.toShort() },
        Byte::class.javaObjectType to { s -> s.toByte() },
    )

    fun parse(url: CubeIntent, data: String?, encoding: Charset) {
        if (!data.isNullOrBlank()) {
            try {
                val bytes = data.toByteArray(encoding)
                parseParameters(url, bytes, encoding)
            } catch (exn: Exception) {
                logger.log(Level.WARNING, "Parsing URL", exn)
            }
        }
    }

    private fun convertHexDigit(b: Byte): Byte {
        val c = b.toInt()
        return when {
            c in '0'.code..'9'.code -> (c - '0'.code).toByte()
            c in 'a'.code..'f'.code -> (c - 'a'.code + 10).toByte()
            c in 'A'.code..'F'.code -> (c - 'A'.code + 10).toByte()
            else -> 0
        }
    }

    private fun castValueTo(value: String?, clazz: Class<*>): Any? {
        if (value == null) return null
        if (clazz == String::class.java) return value
        if (clazz == Char::class.javaObjectType || clazz == Char::class.java) {
            return if (value.isEmpty()) null else value[0]
        }
        val parser = NUMERIC_PARSERS[clazz]
        if (parser != null) {
            return try {
                parser(value)
            } catch (_: NumberFormatException) {
                null
            }
        }
        return null
    }

    private fun putMapEntry(url: CubeIntent, name: String, value: String) {
        val oldValue = url.getParameterValue(name)

        if (oldValue == null) {
            url.setParameter(name, value)
        } else if (oldValue.javaClass.isArray) {
            val arrayType = oldValue.javaClass.componentType
            val arrayLength = ReflectArrayCompat.getLength(oldValue)

            if (arrayLength == 0) {
                val array = ArrayUtils.newInstance(arrayType, 1)
                ReflectArrayCompat.set(array, 0, castValueTo(value, arrayType))
                url.setParameter(name, array)
            } else {
                val array = ArrayUtils.newInstance(arrayType, arrayLength + 1)
                System.arraycopy(oldValue, 0, array, 0, arrayLength)
                ReflectArrayCompat.set(array, arrayLength, castValueTo(value, arrayType))
                url.setParameter(name, array)
            }
        } else {
            val arrayType = oldValue.javaClass
            val array = ArrayUtils.newInstance(arrayType, 2)
            ReflectArrayCompat.set(array, 0, oldValue)
            ReflectArrayCompat.set(array, 1, castValueTo(value, arrayType))
            url.setParameter(name, array)
        }
    }

    fun parseParameters(url: CubeIntent, data: ByteArray?, encoding: Charset) {
        if (data == null || data.isEmpty()) return

        var ix = 0
        var ox = 0
        var key: String? = null

        while (ix < data.size) {
            val c = data[ix++]
            when (c.toInt().toChar()) {
                '&' -> {
                    if (key != null) {
                        putMapEntry(url, key, String(data, 0, ox, encoding))
                        key = null
                    }
                    ox = 0
                }
                '=' -> {
                    if (key == null) {
                        key = String(data, 0, ox, encoding)
                        ox = 0
                    } else {
                        data[ox++] = c
                    }
                }
                '+' -> data[ox++] = ' '.code.toByte()
                '%' -> {
                    val hi = convertHexDigit(data[ix++]).toInt() and 0xFF
                    val lo = convertHexDigit(data[ix++]).toInt() and 0xFF
                    data[ox++] = ((hi shl 4) or lo).toByte()
                }
                else -> data[ox++] = c
            }
        }

        // The last value does not end in '&'. So save it now.
        if (key != null) {
            putMapEntry(url, key, String(data, 0, ox, encoding))
        }
    }
}
