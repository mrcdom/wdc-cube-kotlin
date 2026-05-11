package br.com.wdc.framework.commons.gson

import br.com.wdc.framework.commons.lang.CoerceUtils
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime

object JsonCoerceUtils {

    // :: String

    @JvmStatic
    @JvmOverloads
    fun asString(jr: JsonReader, defaultValue: String? = null): String? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.STRING -> jr.nextString()
        JsonToken.BOOLEAN -> CoerceUtils.asString(jr.nextBoolean(), defaultValue)
        JsonToken.NUMBER -> {
            val numStr = jr.nextString()
            if (isIntegerFormat(numStr)) CoerceUtils.asString(numStr.toLong(), defaultValue)
            else CoerceUtils.asString(numStr.toDouble(), defaultValue)
        }
        else -> throw IOException("No valid value found. JsonReader.peek() = ${jr.peek()}")
    }

    @JvmStatic
    @JvmOverloads
    fun asTrimmedString(jr: JsonReader, defaultValue: String? = null): String? =
        asString(jr, defaultValue)?.trim()

    @JvmStatic
    @JvmOverloads
    fun asLowerCaseString(jr: JsonReader, defaultValue: String? = null): String? =
        asString(jr, defaultValue)?.lowercase()

    @JvmStatic
    @JvmOverloads
    fun asUpperCaseString(jr: JsonReader, defaultValue: String? = null): String? =
        asString(jr, defaultValue)?.uppercase()

    // :: Boolean

    @JvmStatic
    @JvmOverloads
    fun asBoolean(jr: JsonReader, defaultValue: Boolean? = null): Boolean? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.BOOLEAN -> jr.nextBoolean()
        JsonToken.STRING -> CoerceUtils.asBoolean(jr.nextString())
        JsonToken.NUMBER -> {
            val numStr = jr.nextString()
            if (isIntegerFormat(numStr)) CoerceUtils.asBoolean(numStr.toLong(), defaultValue)
            else CoerceUtils.asBoolean(numStr.toDouble(), defaultValue)
        }
        else -> throw newInvalidValueFound(jr)
    }

    // :: Byte

    @JvmStatic
    @JvmOverloads
    fun asByte(jr: JsonReader, defaultValue: Byte? = null): Byte? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.NUMBER -> jr.nextInt().toByte()
        JsonToken.BOOLEAN -> CoerceUtils.asByte(jr.nextBoolean())
        JsonToken.STRING -> CoerceUtils.asByte(jr.nextString())
        else -> throw newInvalidValueFound(jr)
    }

    // :: Short

    @JvmStatic
    @JvmOverloads
    fun asShort(jr: JsonReader, defaultValue: Short? = null): Short? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.NUMBER -> jr.nextInt().toShort()
        JsonToken.BOOLEAN -> CoerceUtils.asShort(jr.nextBoolean())
        JsonToken.STRING -> CoerceUtils.asShort(jr.nextString())
        else -> throw newInvalidValueFound(jr)
    }

    // :: Integer

    @JvmStatic
    @JvmOverloads
    fun asInteger(jr: JsonReader, defaultValue: Int? = null): Int? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.NUMBER -> jr.nextInt()
        JsonToken.BOOLEAN -> CoerceUtils.asInteger(jr.nextBoolean())
        JsonToken.STRING -> CoerceUtils.asInteger(jr.nextString())
        else -> throw newInvalidValueFound(jr)
    }

    // :: Character

    @JvmStatic
    @JvmOverloads
    fun asCharacter(jr: JsonReader, defaultValue: Char? = null): Char? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.STRING -> CoerceUtils.asCharacter(jr.nextString())
        JsonToken.NUMBER -> CoerceUtils.asCharacter(jr.nextInt())
        JsonToken.BOOLEAN -> CoerceUtils.asCharacter(jr.nextBoolean())
        else -> throw newInvalidValueFound(jr)
    }

    // :: Long

    @JvmStatic
    @JvmOverloads
    fun asLong(jr: JsonReader, defaultValue: Long? = null): Long? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.NUMBER -> jr.nextLong()
        JsonToken.BOOLEAN -> CoerceUtils.asLong(jr.nextBoolean())
        JsonToken.STRING -> CoerceUtils.asLong(jr.nextString())
        else -> throw newInvalidValueFound(jr)
    }

    // :: BigInteger

    @JvmStatic
    @JvmOverloads
    fun asBigInteger(jr: JsonReader, defaultValue: BigInteger? = null): BigInteger? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.NUMBER -> CoerceUtils.asBigInteger(jr.nextLong())
        JsonToken.BOOLEAN -> CoerceUtils.asBigInteger(jr.nextBoolean())
        JsonToken.STRING -> CoerceUtils.asBigInteger(jr.nextString())
        else -> throw newInvalidValueFound(jr)
    }

    // :: Float

    @JvmStatic
    @JvmOverloads
    fun asFloat(jr: JsonReader, defaultValue: Float? = null): Float? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.NUMBER -> jr.nextDouble().toFloat()
        JsonToken.BOOLEAN -> CoerceUtils.asFloat(jr.nextBoolean())
        JsonToken.STRING -> CoerceUtils.asFloat(jr.nextString())
        else -> throw newInvalidValueFound(jr)
    }

    // :: Double

    @JvmStatic
    @JvmOverloads
    fun asDouble(jr: JsonReader, defaultValue: Double? = null): Double? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.NUMBER -> jr.nextDouble()
        JsonToken.BOOLEAN -> CoerceUtils.asDouble(jr.nextBoolean())
        JsonToken.STRING -> CoerceUtils.asDouble(jr.nextString())
        else -> throw newInvalidValueFound(jr)
    }

    // :: BigDecimal

    @JvmStatic
    @JvmOverloads
    fun asBigDecimal(jr: JsonReader, defaultValue: BigDecimal? = null): BigDecimal? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.NUMBER -> CoerceUtils.asBigDecimal(jr.nextDouble())
        JsonToken.BOOLEAN -> CoerceUtils.asBigDecimal(jr.nextBoolean())
        JsonToken.STRING -> CoerceUtils.asBigDecimal(jr.nextString())
        else -> throw newInvalidValueFound(jr)
    }

    // :: Date

    @JvmStatic
    @JvmOverloads
    fun asDate(jr: JsonReader, defaultValue: java.util.Date? = null): java.util.Date? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.NUMBER -> CoerceUtils.asDate(jr.nextDouble())
        JsonToken.BOOLEAN -> CoerceUtils.asDate(jr.nextBoolean())
        JsonToken.STRING -> CoerceUtils.asDate(jr.nextString())
        else -> throw newInvalidValueFound(jr)
    }

    // :: Timestamp

    @JvmStatic
    @JvmOverloads
    fun asTimestamp(jr: JsonReader, defaultValue: java.sql.Timestamp? = null): java.sql.Timestamp? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.NUMBER -> CoerceUtils.asTimestamp(jr.nextDouble())
        JsonToken.BOOLEAN -> CoerceUtils.asTimestamp(jr.nextBoolean())
        JsonToken.STRING -> CoerceUtils.asTimestamp(jr.nextString())
        else -> throw newInvalidValueFound(jr)
    }

    // :: SqlDate

    @JvmStatic
    @JvmOverloads
    fun asSqlDate(jr: JsonReader, defaultValue: java.sql.Date? = null): java.sql.Date? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.NUMBER -> CoerceUtils.asSqlDate(jr.nextDouble())
        JsonToken.BOOLEAN -> CoerceUtils.asSqlDate(jr.nextBoolean())
        JsonToken.STRING -> CoerceUtils.asSqlDate(jr.nextString())
        else -> throw newInvalidValueFound(jr)
    }

    // :: LocalDate

    @JvmStatic
    @JvmOverloads
    fun asLocalDate(jr: JsonReader, defaultValue: LocalDate? = null): LocalDate? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.NUMBER -> CoerceUtils.asLocalDate(jr.nextDouble())
        JsonToken.BOOLEAN -> CoerceUtils.asLocalDate(jr.nextBoolean())
        JsonToken.STRING -> CoerceUtils.asLocalDate(jr.nextString())
        else -> throw newInvalidValueFound(jr)
    }

    // :: LocalDateTime

    @JvmStatic
    @JvmOverloads
    fun asLocalDateTime(jr: JsonReader, defaultValue: LocalDateTime? = null): LocalDateTime? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.NUMBER -> CoerceUtils.asLocalDateTime(jr.nextDouble())
        JsonToken.BOOLEAN -> CoerceUtils.asLocalDateTime(jr.nextBoolean())
        JsonToken.STRING -> CoerceUtils.asLocalDateTime(jr.nextString())
        else -> throw newInvalidValueFound(jr)
    }

    // :: OffsetDateTime

    @JvmStatic
    @JvmOverloads
    fun asOffsetDateTime(jr: JsonReader, defaultValue: OffsetDateTime? = null): OffsetDateTime? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.NUMBER -> CoerceUtils.asOffsetDateTime(jr.nextDouble())
        JsonToken.BOOLEAN -> CoerceUtils.asOffsetDateTime(jr.nextBoolean())
        JsonToken.STRING -> CoerceUtils.asOffsetDateTime(jr.nextString())
        else -> throw newInvalidValueFound(jr)
    }

    // :: ZonedDateTime

    @JvmStatic
    @JvmOverloads
    fun asZonedDateTime(jr: JsonReader, defaultValue: ZonedDateTime? = null): ZonedDateTime? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.NUMBER -> CoerceUtils.asZonedDateTime(jr.nextDouble())
        JsonToken.BOOLEAN -> CoerceUtils.asZonedDateTime(jr.nextBoolean())
        JsonToken.STRING -> CoerceUtils.asZonedDateTime(jr.nextString())
        else -> throw newInvalidValueFound(jr)
    }

    // :: ByteArray

    @JvmStatic
    @JvmOverloads
    fun asByteArray(jr: JsonReader, defaultValue: ByteArray? = null): ByteArray? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.STRING -> CoerceUtils.asByteArray(jr.nextString())
        JsonToken.BOOLEAN -> CoerceUtils.asByteArray(jr.nextBoolean(), defaultValue)
        JsonToken.NUMBER -> {
            val numStr = jr.nextString()
            if (isIntegerFormat(numStr)) CoerceUtils.asByteArray(numStr.toLong(), defaultValue)
            else CoerceUtils.asByteArray(numStr.toDouble(), defaultValue)
        }
        else -> throw newInvalidValueFound(jr)
    }

    @JvmStatic
    @JvmOverloads
    fun asByteArrayFromHex(jr: JsonReader, defaultValue: ByteArray? = null): ByteArray? = when (jr.peek()) {
        JsonToken.NULL -> { jr.nextNull(); defaultValue }
        JsonToken.STRING -> CoerceUtils.asByteArrayFromHex(jr.nextString())
        JsonToken.BOOLEAN -> CoerceUtils.asByteArrayFromHex(jr.nextBoolean(), defaultValue)
        JsonToken.NUMBER -> {
            val numStr = jr.nextString()
            if (isIntegerFormat(numStr)) CoerceUtils.asByteArrayFromHex(numStr.toLong(), defaultValue)
            else CoerceUtils.asByteArrayFromHex(numStr.toDouble(), defaultValue)
        }
        else -> throw newInvalidValueFound(jr)
    }

    // :: Internals

    private fun isIntegerFormat(numStr: String): Boolean =
        '.' !in numStr && 'e' !in numStr && 'E' !in numStr

    private fun newInvalidValueFound(jr: JsonReader): IOException =
        try {
            IOException("No valid value found. JsonReader.peek() = ${jr.peek()}")
        } catch (e: IOException) {
            IOException("No valid value found. JsonReader.peek() not available", e)
        }
}
