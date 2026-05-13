package br.com.wdc.framework.commons.lang

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.datetime.*

object CoerceUtils {

    // :: String

    fun asString(v: Any?, defaultValue: String? = null): String? = when (v) {
        null -> defaultValue
        is String -> v
        else -> v.toString()
    }

    fun asTrimmedString(v: Any?, defaultValue: String? = null): String? = when (v) {
        null -> defaultValue?.trim()
        else -> v.toString().trim()
    }

    fun asLowerCaseString(v: Any?, defaultValue: String? = null): String? = when (v) {
        null -> defaultValue
        else -> v.toString().lowercase()
    }

    fun asUpperCaseString(v: Any?, defaultValue: String? = null): String? = when (v) {
        null -> defaultValue?.uppercase()
        else -> v.toString().uppercase()
    }

    // :: Boolean

    fun asBoolean(v: Any?, defaultValue: Boolean? = null): Boolean? = when (v) {
        null -> defaultValue
        is Boolean -> v
        is Number -> v.toInt() != 0
        is Char -> when (v) {
            's', 'S', 't', 'T' -> true
            'n', 'N', 'f', 'F' -> false
            else -> defaultValue
        }
        is String -> when {
            v.isEmpty() -> defaultValue
            v.length == 1 -> when (v[0]) {
                's', 'S', 't', 'T' -> true
                'n', 'N', 'f', 'F' -> false
                else -> defaultValue
            }
            v.equals("true", ignoreCase = true) || v.equals("sim", ignoreCase = true) -> true
            v.equals("false", ignoreCase = true) || v.equals("nao", ignoreCase = true) -> false
            else -> throw IllegalArgumentException(errorMessage(v))
        }
        else -> throw IllegalArgumentException(errorMessage(v))
    }

    // :: Byte

    fun asByte(v: Any?, defaultValue: Byte? = null): Byte? = when (v) {
        null -> defaultValue
        is Byte -> v
        is Number -> v.toByte()
        is String -> if (v.isEmpty()) defaultValue else v.toByte()
        is Boolean -> if (v) 1 else 0
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: Short

    fun asShort(v: Any?, defaultValue: Short? = null): Short? = when (v) {
        null -> defaultValue
        is Short -> v
        is Number -> v.toShort()
        is String -> if (v.isEmpty()) defaultValue else v.toShort()
        is Boolean -> if (v) 1 else 0
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: Integer

    fun asInteger(v: Any?, defaultValue: Int? = null): Int? = when (v) {
        null -> defaultValue
        is Int -> v
        is Number -> v.toInt()
        is String -> if (v.isEmpty()) defaultValue else v.toInt()
        is Boolean -> if (v) 1 else 0
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: Character

    fun asCharacter(v: Any?, defaultValue: Char? = null): Char? = when (v) {
        null -> defaultValue
        is Char -> v
        is Number -> v.toInt().toChar()
        is String -> if (v.isEmpty()) defaultValue else v[0]
        is Boolean -> if (v) 'T' else 'F'
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: Long

    fun asLong(v: Any?, defaultValue: Long? = null): Long? = when (v) {
        null -> defaultValue
        is Long -> v
        is Number -> v.toLong()
        is String -> if (v.isEmpty()) defaultValue else v.toLong()
        is Boolean -> if (v) 1L else 0L
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: BigInteger

    fun asBigInteger(v: Any?, defaultValue: BigInteger? = null): BigInteger? = when (v) {
        null -> defaultValue
        is BigInteger -> v
        is BigDecimal -> v.toBigInteger()
        is Number -> BigInteger.fromLong(v.toLong())
        is String -> if (v.isEmpty()) defaultValue else BigInteger.parseString(v)
        is Boolean -> if (v) BigInteger.ONE else BigInteger.ZERO
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: Float

    fun asFloat(v: Any?, defaultValue: Float? = null): Float? = when (v) {
        null -> defaultValue
        is Float -> v
        is Number -> v.toFloat()
        is String -> if (v.isEmpty()) defaultValue else v.toFloat()
        is Boolean -> if (v) 1.0f else 0.0f
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: Double

    fun asDouble(v: Any?, defaultValue: Double? = null): Double? = when (v) {
        null -> defaultValue
        is Double -> v
        is Number -> v.toDouble()
        is String -> if (v.isEmpty()) defaultValue else v.toDouble()
        is Boolean -> if (v) 1.0 else 0.0
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: BigDecimal

    fun asBigDecimal(v: Any?, defaultValue: BigDecimal? = null): BigDecimal? = when (v) {
        null -> defaultValue
        is BigDecimal -> v
        is BigInteger -> BigDecimal.fromBigInteger(v)
        is Long -> BigDecimal.fromLong(v)
        is Short -> BigDecimal.fromLong(v.toLong())
        is Byte -> BigDecimal.fromLong(v.toLong())
        is Number -> BigDecimal.fromDouble(v.toDouble())
        is String -> if (v.isEmpty()) defaultValue else BigDecimal.parseString(v)
        is Boolean -> if (v) BigDecimal.ONE else BigDecimal.ZERO
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: LocalDate (kotlinx-datetime)

    fun asLocalDate(v: Any?, defaultValue: LocalDate? = null): LocalDate? = when (v) {
        null -> defaultValue
        is LocalDate -> v
        is LocalDateTime -> v.date
        is Instant -> v.toLocalDateTime(TimeZone.currentSystemDefault()).date
        is String -> if (v.isEmpty()) defaultValue else LocalDate.parse(v)
        else -> throw IllegalArgumentException(errorMessage(v))
    }

    // :: LocalDateTime (kotlinx-datetime)

    fun asLocalDateTime(v: Any?, defaultValue: LocalDateTime? = null): LocalDateTime? = when (v) {
        null -> defaultValue
        is LocalDateTime -> v
        is LocalDate -> LocalDateTime(v, LocalTime(0, 0))
        is Instant -> v.toLocalDateTime(TimeZone.currentSystemDefault())
        is String -> if (v.isEmpty()) defaultValue else LocalDateTime.parse(v)
        else -> throw IllegalArgumentException(errorMessage(v))
    }

    // :: Instant (kotlinx-datetime)

    fun asInstant(v: Any?, defaultValue: Instant? = null): Instant? = when (v) {
        null -> defaultValue
        is Instant -> v
        is LocalDateTime -> v.toInstant(TimeZone.currentSystemDefault())
        is LocalDate -> LocalDateTime(v, LocalTime(0, 0)).toInstant(TimeZone.currentSystemDefault())
        is String -> if (v.isEmpty()) defaultValue else Instant.parse(v)
        else -> throw IllegalArgumentException(errorMessage(v))
    }

    // :: ByteArray (Base64 - pure Kotlin)

    fun asByteArray(v: Any?, defaultValue: ByteArray? = null): ByteArray? = when (v) {
        null -> defaultValue
        is ByteArray -> v
        is Byte -> byteArrayOf(v)
        is String -> if (v.isEmpty()) defaultValue else decodeBase64(v)
        else -> throw IllegalArgumentException(errorMessage(v))
    }

    // :: Internal helpers

    private fun errorMessage(v: Any): String = "The value cannot be parsed to: '${v::class.simpleName}'"

    private fun decodeBase64(s: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val clean = s.filter { it != '=' && it != '\n' && it != '\r' }
        val bytes = ByteArray(clean.length * 3 / 4)
        var idx = 0
        var i = 0
        while (i < clean.length) {
            val b0 = alphabet.indexOf(clean[i++])
            val b1 = if (i < clean.length) alphabet.indexOf(clean[i++]) else 0
            val b2 = if (i < clean.length) alphabet.indexOf(clean[i++]) else 0
            val b3 = if (i < clean.length) alphabet.indexOf(clean[i++]) else 0
            val triple = (b0 shl 18) or (b1 shl 12) or (b2 shl 8) or b3
            if (idx < bytes.size) bytes[idx++] = (triple shr 16 and 0xFF).toByte()
            if (idx < bytes.size) bytes[idx++] = (triple shr 8 and 0xFF).toByte()
            if (idx < bytes.size) bytes[idx++] = (triple and 0xFF).toByte()
        }
        return bytes.copyOf(idx)
    }
}
