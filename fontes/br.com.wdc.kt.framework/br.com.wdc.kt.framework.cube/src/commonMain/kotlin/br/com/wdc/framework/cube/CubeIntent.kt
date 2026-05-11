package br.com.wdc.framework.cube

class CubeIntent {

    var place: CubePlace? = null

    internal val parameters: MutableMap<String, Any?> = LinkedHashMap()

    private var attributes: MutableMap<String, Any?>? = null

    // :: Attributes

    fun setAttribute(name: String, value: Any?) {
        val attrs = attributes ?: HashMap<String, Any?>().also { attributes = it }
        attrs[name] = value
    }

    fun getAttribute(name: String): Any? = attributes?.get(name)

    fun removeAttribute(name: String): Any? = attributes?.remove(name)

    fun setViewSlot(name: String, slot: CubeViewSlot) {
        setAttribute(name, slot)
    }

    fun getViewSlot(name: String): CubeViewSlot? = getAttribute(name) as? CubeViewSlot

    // :: Parameters

    fun clearParameters() {
        parameters.clear()
    }

    fun removeParameter(name: String): Any? = parameters.remove(name)

    fun setParameter(name: String, value: Any?) {
        if (value == null) parameters.remove(name)
        else parameters[name] = value
    }

    fun getParameterValue(name: String): Any? = parameters[name]

    fun getParameterAsString(name: String, defaultValue: String? = null): String? {
        val value = parameters[name] ?: return defaultValue
        return value.toString()
    }

    fun getParameterAsDouble(name: String, defaultValue: Double? = null): Double? {
        val value = parameters[name]
        if (value is Double) return value
        if (value != null) {
            try { return value.toString().toDouble() }
            catch (_: NumberFormatException) { /* ignored */ }
        }
        return defaultValue
    }

    fun getParameterAsFloat(name: String, defaultValue: Float? = null): Float? {
        val value = parameters[name]
        if (value is Float) return value
        if (value != null) {
            try { return value.toString().toFloat() }
            catch (_: NumberFormatException) { /* ignored */ }
        }
        return defaultValue
    }

    fun getParameterAsLong(name: String, defaultValue: Long? = null): Long? {
        val value = parameters[name]
        if (value is Long) return value
        if (value != null) {
            try { return value.toString().toLong() }
            catch (_: NumberFormatException) { /* ignored */ }
        }
        return defaultValue
    }

    fun getParameterAsInteger(name: String, defaultValue: Int? = null): Int? {
        val value = parameters[name]
        if (value is Int) return value
        if (value != null) {
            try { return value.toString().toInt() }
            catch (_: NumberFormatException) { /* ignored */ }
        }
        return defaultValue
    }

    fun getParameterAsShort(name: String, defaultValue: Short? = null): Short? {
        val value = parameters[name]
        if (value is Short) return value
        if (value != null) {
            try { return value.toString().toShort() }
            catch (_: NumberFormatException) { /* ignored */ }
        }
        return defaultValue
    }

    fun getParameterAsByte(name: String, defaultValue: Byte? = null): Byte? {
        val value = parameters[name]
        if (value is Byte) return value
        if (value != null) {
            try { return value.toString().toByte() }
            catch (_: NumberFormatException) { /* ignored */ }
        }
        return defaultValue
    }

    fun getParameterAsCharacter(name: String, defaultValue: Char? = null): Char? {
        val value = parameters[name]
        if (value is Char) return value
        if (value != null) {
            val svalue = value.toString()
            return if (svalue.isBlank()) defaultValue else svalue[0]
        }
        return defaultValue
    }

    override fun toString(): String {
        val placeName = place?.placeName ?: "unknown"
        if (parameters.isEmpty()) return placeName
        val qs = parameters.entries.joinToString("&") { (k, v) ->
            "${encodeComponent(k)}=${encodeComponent(v.toString())}"
        }
        return "$placeName?$qs"
    }

    companion object {
        fun parse(placeStr: String?): CubeIntent {
            val intent = CubeIntent()
            if (!placeStr.isNullOrBlank()) {
                val parts = placeStr.split("?", limit = 2)
                intent.place = GenericPlace(-1, parts[0])
                if (parts.size > 1) {
                    parseQueryString(intent, parts[1])
                }
            } else {
                intent.place = GenericPlace(-1, "unknown")
            }
            return intent
        }
    }
}

private class GenericPlace(
    override val id: Int,
    override val placeName: String,
) : CubePlace {
    override fun <A : CubeApplication> presenterFactory(): (A) -> CubePresenter =
        throw AssertionError("Must not be invoked")
}

private fun parseQueryString(intent: CubeIntent, query: String) {
    if (query.isBlank()) return
    for (pair in query.split("&")) {
        val eq = pair.indexOf('=')
        if (eq >= 0) {
            val key = decodeComponent(pair.substring(0, eq))
            val value = decodeComponent(pair.substring(eq + 1))
            intent.setParameter(key, value)
        } else {
            intent.setParameter(decodeComponent(pair), "")
        }
    }
}

private fun decodeComponent(s: String): String {
    val sb = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '+' -> { sb.append(' '); i++ }
            c == '%' && i + 2 < s.length -> {
                val hi = hexDigit(s[i + 1])
                val lo = hexDigit(s[i + 2])
                if (hi >= 0 && lo >= 0) {
                    sb.append(((hi shl 4) or lo).toChar())
                    i += 3
                } else {
                    sb.append(c); i++
                }
            }
            else -> { sb.append(c); i++ }
        }
    }
    return sb.toString()
}

private fun hexDigit(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> -1
}

private fun encodeComponent(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) {
        when {
            c.isLetterOrDigit() || c == '-' || c == '_' || c == '.' || c == '~' -> sb.append(c)
            c == ' ' -> sb.append('+')
            else -> {
                val bytes = c.toString().encodeToByteArray()
                for (b in bytes) {
                    sb.append('%')
                    sb.append(HEX[(b.toInt() shr 4) and 0x0F])
                    sb.append(HEX[b.toInt() and 0x0F])
                }
            }
        }
    }
    return sb.toString()
}

private val HEX = charArrayOf('0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F')
