package br.com.wdc.framework.cube.util

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.CubeIntent

object QueryStringParser {

    private val logger = Log.getLogger("QueryStringParser")

    fun parse(url: CubeIntent, data: String?) {
        if (!data.isNullOrBlank()) {
            try {
                parseParameters(url, data)
            } catch (exn: Exception) {
                logger.warn("Parsing URL: {}", exn.message)
            }
        }
    }

    private fun convertHexDigit(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> 0
    }

    private fun putMapEntry(url: CubeIntent, name: String, value: String) {
        val oldValue = url.getParameterValue(name)

        if (oldValue == null) {
            url.setParameter(name, value)
        } else if (oldValue is List<*>) {
            @Suppress("UNCHECKED_CAST")
            val list = oldValue as MutableList<String>
            list.add(value)
        } else {
            // oldValue is a single String — promote to list
            val list = mutableListOf(oldValue.toString(), value)
            url.setParameter(name, list)
        }
    }

    fun parseParameters(url: CubeIntent, data: String) {
        if (data.isEmpty()) return

        val sb = StringBuilder()
        var key: String? = null
        var i = 0

        while (i < data.length) {
            val c = data[i++]
            when (c) {
                '&' -> {
                    if (key != null) {
                        putMapEntry(url, key, sb.toString())
                        key = null
                    }
                    sb.clear()
                }
                '=' -> {
                    if (key == null) {
                        key = sb.toString()
                        sb.clear()
                    } else {
                        sb.append(c)
                    }
                }
                '+' -> sb.append(' ')
                '%' -> {
                    if (i + 1 < data.length) {
                        val hi = convertHexDigit(data[i++])
                        val lo = convertHexDigit(data[i++])
                        // For multi-byte UTF-8, collect all percent-encoded bytes
                        val firstByte = ((hi shl 4) or lo).toByte()
                        val expectedLen = utf8ByteCount(firstByte)
                        if (expectedLen > 1) {
                            val bytes = ByteArray(expectedLen)
                            bytes[0] = firstByte
                            var j = 1
                            while (j < expectedLen && i + 2 < data.length && data[i] == '%') {
                                i++ // skip '%'
                                val h = convertHexDigit(data[i++])
                                val l = convertHexDigit(data[i++])
                                bytes[j++] = ((h shl 4) or l).toByte()
                            }
                            sb.append(bytes.decodeToString(0, j))
                        } else {
                            sb.append(((hi shl 4) or lo).toChar())
                        }
                    }
                }
                else -> sb.append(c)
            }
        }

        // The last value does not end in '&'. So save it now.
        if (key != null) {
            putMapEntry(url, key, sb.toString())
        }
    }

    private fun utf8ByteCount(firstByte: Byte): Int {
        val b = firstByte.toInt() and 0xFF
        return when {
            b and 0x80 == 0 -> 1
            b and 0xE0 == 0xC0 -> 2
            b and 0xF0 == 0xE0 -> 3
            b and 0xF8 == 0xF0 -> 4
            else -> 1
        }
    }
}
