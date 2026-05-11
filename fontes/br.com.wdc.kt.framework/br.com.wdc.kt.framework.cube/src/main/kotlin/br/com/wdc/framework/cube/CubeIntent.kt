package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.framework.cube.util.QueryStringBuilder
import br.com.wdc.framework.cube.util.QueryStringParser
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.logging.Level
import java.util.logging.Logger

class CubeIntent {

    var place: CubePlace? = null

    private val parameters: MutableMap<String, Any?> = LinkedHashMap()

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

    fun getParameterAsString(name: String, defaultValue: String?): String? {
        val svalue = CoerceUtils.asString(parameters[name])
        return svalue ?: defaultValue
    }

    fun getParameterAsBigDecimal(name: String, defaultValue: BigDecimal?): BigDecimal? {
        val value = parameters[name]
        if (value is BigDecimal) return value
        if (value != null) {
            try { return BigDecimal(CoerceUtils.asString(value)) }
            catch (e: NumberFormatException) { logger.log(Level.WARNING, "NumberFormatException", e) }
        }
        return defaultValue
    }

    fun getParameterAsDouble(name: String, defaultValue: Double?): Double? {
        val value = parameters[name]
        if (value is Double) return value
        if (value != null) {
            try { return CoerceUtils.asString(value)?.toDouble() }
            catch (e: NumberFormatException) { logger.log(Level.WARNING, "NumberFormatException", e) }
        }
        return defaultValue
    }

    fun getParameterAsFloat(name: String, defaultValue: Float?): Float? {
        val value = parameters[name]
        if (value is Float) return value
        if (value != null) {
            try { return CoerceUtils.asString(value)?.toFloat() }
            catch (e: NumberFormatException) { logger.log(Level.WARNING, "NumberFormatException", e) }
        }
        return defaultValue
    }

    fun getParameterAsBigInteger(name: String, defaultValue: BigInteger?): BigInteger? {
        val value = parameters[name]
        if (value is BigInteger) return value
        if (value != null) {
            try { return BigInteger(CoerceUtils.asString(value)) }
            catch (e: NumberFormatException) { logger.log(Level.WARNING, "NumberFormatException", e) }
        }
        return defaultValue
    }

    fun getParameterAsLong(name: String, defaultValue: Long?): Long? {
        val value = parameters[name]
        if (value is Long) return value
        if (value != null) {
            try { return CoerceUtils.asString(value)?.toLong() }
            catch (e: NumberFormatException) { logger.log(Level.WARNING, "NumberFormatException", e) }
        }
        return defaultValue
    }

    fun getParameterAsInteger(name: String, defaultValue: Int?): Int? {
        val value = parameters[name]
        if (value is Int) return value
        if (value != null) {
            try { return CoerceUtils.asString(value)?.toInt() }
            catch (e: NumberFormatException) { logger.log(Level.WARNING, "NumberFormatException", e) }
        }
        return defaultValue
    }

    fun getParameterAsShort(name: String, defaultValue: Short?): Short? {
        val value = parameters[name]
        if (value is Short) return value
        if (value != null) {
            try { return CoerceUtils.asString(value)?.toShort() }
            catch (e: NumberFormatException) { logger.log(Level.WARNING, "NumberFormatException", e) }
        }
        return defaultValue
    }

    fun getParameterAsByte(name: String, defaultValue: Byte?): Byte? {
        val value = parameters[name]
        if (value is Byte) return value
        if (value != null) {
            try { return CoerceUtils.asString(value)?.toByte() }
            catch (e: NumberFormatException) { logger.log(Level.WARNING, "NumberFormatException", e) }
        }
        return defaultValue
    }

    fun getParameterAsCharacter(name: String, defaultValue: Char?): Char? {
        val value = parameters[name]
        if (value is Char) return value
        if (value != null) {
            try {
                val svalue = CoerceUtils.asString(value)
                return if (svalue.isNullOrBlank()) defaultValue else svalue[0]
            } catch (e: NumberFormatException) {
                logger.log(Level.WARNING, "NumberFormatException", e)
            }
        }
        return defaultValue
    }

    fun getQueryString(): String {
        if (parameters.isEmpty()) return ""
        val builder = QueryStringBuilder()
        @Suppress("UNCHECKED_CAST")
        builder.append(parameters as Map<String, Any>)
        return builder.toString()
    }

    override fun toString(): String {
        val queryString = getQueryString()
        val placeName = place?.placeName ?: "unknown"
        return if (queryString.isBlank()) placeName else "$placeName?$queryString"
    }

    companion object {
        private val logger = Logger.getLogger(CubeIntent::class.java.name)

        @JvmStatic
        fun parse(placeStr: String?): CubeIntent {
            val intent = CubeIntent()
            if (!placeStr.isNullOrBlank()) {
                val parts = placeStr.split("?", limit = 2)
                intent.place = GenericPlace(-1, parts[0])
                if (parts.size > 1) {
                    QueryStringParser.parse(intent, parts[1], StandardCharsets.UTF_8)
                }
            } else {
                intent.place = GenericPlace(-1, "unknown")
            }
            return intent
        }

        private class GenericPlace(
            override val id: Int,
            override val placeName: String,
        ) : CubePlace {
            override fun <A : CubeApplication> presenterFactory(): (A) -> CubePresenter =
                throw AssertionError("Must not be invoked")
        }
    }
}
