package br.com.wdc.framework.cube.util

class QueryStringBuilder {

    private val sb = StringBuilder()

    fun append(parameters: Map<String, Any>) {
        for ((key, value) in parameters) {
            if (value is Array<*>) {
                for (element in value) {
                    if (element != null) {
                        appendPair(key, element.toString())
                    }
                }
            } else {
                appendPair(key, value.toString())
            }
        }
    }

    private fun appendPair(name: String, value: String) {
        if (sb.isNotEmpty()) {
            sb.append('&')
        }
        sb.append(urlEncode(name))
        sb.append('=')
        sb.append(urlEncode(value))
    }

    override fun toString(): String = sb.toString()

    companion object {
        private fun urlEncode(input: String): String {
            val result = StringBuilder(input.length * 2)
            for (byte in input.encodeToByteArray()) {
                val c = byte.toInt() and 0xFF
                when {
                    c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code ||
                    c in '0'.code..'9'.code || c == '-'.code || c == '_'.code ||
                    c == '.'.code || c == '*'.code -> result.append(c.toChar())
                    c == ' '.code -> result.append('+')
                    else -> {
                        result.append('%')
                        result.append(HEX_CHARS[c shr 4])
                        result.append(HEX_CHARS[c and 0x0F])
                    }
                }
            }
            return result.toString()
        }

        private val HEX_CHARS = "0123456789ABCDEF".toCharArray()
    }
}
