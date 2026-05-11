package br.com.wdc.framework.commons.lang

import br.com.wdc.framework.commons.convert.DateUtil
import java.math.BigDecimal
import java.math.BigInteger
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAccessor
import java.util.Base64
import java.util.HexFormat

object CoerceUtils {

    // :: String

    @JvmStatic
    @JvmOverloads
    fun asString(v: Any?, defaultValue: String? = null): String? = when (v) {
        null -> defaultValue
        is String -> v
        else -> v.toString()
    }

    @JvmStatic
    @JvmOverloads
    fun asTrimmedString(v: Any?, defaultValue: String? = null): String? = when (v) {
        null -> defaultValue?.trim()
        else -> v.toString().trim()
    }

    @JvmStatic
    @JvmOverloads
    fun asLowerCaseString(v: Any?, defaultValue: String? = null): String? = when (v) {
        null -> defaultValue
        else -> v.toString().lowercase()
    }

    @JvmStatic
    @JvmOverloads
    fun asUpperCaseString(v: Any?, defaultValue: String? = null): String? = when (v) {
        null -> defaultValue?.uppercase()
        else -> v.toString().uppercase()
    }

    // :: Boolean

    @JvmStatic
    @JvmOverloads
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

    @JvmStatic
    @JvmOverloads
    fun asByte(v: Any?, defaultValue: Byte? = null): Byte? = when (v) {
        null -> defaultValue
        is Byte -> v
        is Number -> v.toByte()
        is String -> if (v.isEmpty()) defaultValue else v.toByte()
        is Boolean -> if (v) 1 else 0
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: Short

    @JvmStatic
    @JvmOverloads
    fun asShort(v: Any?, defaultValue: Short? = null): Short? = when (v) {
        null -> defaultValue
        is Short -> v
        is Number -> v.toShort()
        is String -> if (v.isEmpty()) defaultValue else v.toShort()
        is Boolean -> if (v) 1 else 0
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: Integer

    @JvmStatic
    @JvmOverloads
    fun asInteger(v: Any?, defaultValue: Int? = null): Int? = when (v) {
        null -> defaultValue
        is Int -> v
        is Number -> v.toInt()
        is String -> if (v.isEmpty()) defaultValue else v.toInt()
        is Boolean -> if (v) 1 else 0
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: Character

    @JvmStatic
    @JvmOverloads
    fun asCharacter(v: Any?, defaultValue: Char? = null): Char? = when (v) {
        null -> defaultValue
        is Char -> v
        is Number -> v.toInt().toChar()
        is String -> if (v.isEmpty()) defaultValue else v[0]
        is Boolean -> if (v) 'T' else 'F'
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: Long

    @JvmStatic
    @JvmOverloads
    fun asLong(v: Any?, defaultValue: Long? = null): Long? = when (v) {
        null -> defaultValue
        is Long -> v
        is Number -> v.toLong()
        is String -> if (v.isEmpty()) defaultValue else v.toLong()
        is Boolean -> if (v) 1L else 0L
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: BigInteger

    @JvmStatic
    @JvmOverloads
    fun asBigInteger(v: Any?, defaultValue: BigInteger? = null): BigInteger? = when (v) {
        null -> defaultValue
        is BigInteger -> v
        is BigDecimal -> v.toBigInteger()
        is Number -> BigInteger.valueOf(v.toLong())
        is String -> if (v.isEmpty()) defaultValue else BigInteger(v)
        is Boolean -> if (v) BigInteger.ONE else BigInteger.ZERO
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: Float

    @JvmStatic
    @JvmOverloads
    fun asFloat(v: Any?, defaultValue: Float? = null): Float? = when (v) {
        null -> defaultValue
        is Float -> v
        is Number -> v.toFloat()
        is String -> if (v.isEmpty()) defaultValue else v.toFloat()
        is Boolean -> if (v) 1.0f else 0.0f
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: Double

    @JvmStatic
    @JvmOverloads
    fun asDouble(v: Any?, defaultValue: Double? = null): Double? = when (v) {
        null -> defaultValue
        is Double -> v
        is Number -> v.toDouble()
        is String -> if (v.isEmpty()) defaultValue else v.toDouble()
        is Boolean -> if (v) 1.0 else 0.0
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: BigDecimal

    @JvmStatic
    @JvmOverloads
    fun asBigDecimal(v: Any?, defaultValue: BigDecimal? = null): BigDecimal? = when (v) {
        null -> defaultValue
        is BigDecimal -> v
        is BigInteger -> BigDecimal(v)
        is Long -> BigDecimal.valueOf(v)
        is Short -> BigDecimal.valueOf(v.toLong())
        is Byte -> BigDecimal.valueOf(v.toLong())
        is Number -> BigDecimal.valueOf(v.toDouble())
        is String -> if (v.isEmpty()) defaultValue else BigDecimal(v)
        is Boolean -> if (v) BigDecimal.ONE else BigDecimal.ZERO
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: Number

    @JvmStatic
    @JvmOverloads
    fun asNumber(v: Any?, defaultValue: Number? = null): Number? = when {
        v == null -> defaultValue
        v is Number -> v
        defaultValue == null -> asBigDecimal(v)
        defaultValue is Int -> asInteger(v, defaultValue)
        defaultValue is Long -> asLong(v, defaultValue)
        defaultValue is Short -> asShort(v, defaultValue)
        defaultValue is Byte -> asByte(v, defaultValue)
        defaultValue is Double -> asDouble(v, defaultValue)
        defaultValue is Float -> asFloat(v, defaultValue)
        defaultValue is BigInteger -> asBigInteger(v, defaultValue)
        defaultValue is BigDecimal -> asBigDecimal(v, defaultValue)
        else -> throw NumberFormatException(errorMessage(v))
    }

    // :: Date

    @JvmStatic
    @JvmOverloads
    fun asDate(v: Any?, defaultValue: java.util.Date? = null): java.util.Date? = when (v) {
        null -> defaultValue
        is java.util.Date -> {
            if (v.javaClass != java.util.Date::class.java) java.util.Date(v.time)
            else v
        }
        is LocalDate -> java.util.Date(LocalDateTime.of(v, LocalTime.MIN).toInstant(DateUtil.sysZoneOffset).toEpochMilli())
        is LocalDateTime -> java.util.Date(v.toInstant(DateUtil.sysZoneOffset).toEpochMilli())
        is OffsetDateTime -> java.util.Date(v.toInstant().toEpochMilli())
        is ZonedDateTime -> java.util.Date(v.toInstant().toEpochMilli())
        is TemporalAccessor -> {
            val dt = LocalDateTime.from(v)
            java.util.Date(dt.toInstant(DateUtil.sysZoneOffset).toEpochMilli())
        }
        is String -> {
            if (v.isEmpty()) defaultValue
            else java.util.Date(toInstant(parseFlexibleTemporal(v)).toEpochMilli())
        }
        else -> throw IllegalArgumentException(errorMessage(v))
    }

    // :: Timestamp

    @JvmStatic
    @JvmOverloads
    fun asTimestamp(v: Any?, defaultValue: java.sql.Timestamp? = null): java.sql.Timestamp? = when (v) {
        null -> defaultValue
        is java.sql.Timestamp -> v
        is java.util.Date -> java.sql.Timestamp(v.time)
        is LocalDate -> java.sql.Timestamp.valueOf(LocalDateTime.of(v, LocalTime.MIN))
        is LocalDateTime -> java.sql.Timestamp.valueOf(v)
        is OffsetDateTime -> java.sql.Timestamp.valueOf(v.toLocalDateTime())
        is ZonedDateTime -> java.sql.Timestamp.valueOf(v.toLocalDateTime())
        is TemporalAccessor -> java.sql.Timestamp.valueOf(LocalDateTime.from(v))
        is String -> {
            if (v.isEmpty()) defaultValue
            else java.sql.Timestamp.valueOf(toLocalDateTime(parseFlexibleTemporal(v)))
        }
        else -> throw IllegalArgumentException(errorMessage(v))
    }

    // :: SqlDate

    @JvmStatic
    @JvmOverloads
    fun asSqlDate(v: Any?, defaultValue: java.sql.Date? = null): java.sql.Date? = when (v) {
        null -> defaultValue
        is java.sql.Date -> v
        is java.util.Date -> java.sql.Date.valueOf(localDateOf(v))
        is LocalDate -> java.sql.Date.valueOf(v)
        is LocalDateTime -> java.sql.Date.valueOf(v.toLocalDate())
        is OffsetDateTime -> java.sql.Date.valueOf(v.toLocalDate())
        is ZonedDateTime -> java.sql.Date.valueOf(v.toLocalDate())
        is TemporalAccessor -> java.sql.Date.valueOf(LocalDate.from(v))
        is String -> {
            if (v.isEmpty()) defaultValue
            else java.sql.Date.valueOf(toLocalDate(parseFlexibleTemporal(v)))
        }
        else -> throw IllegalArgumentException(errorMessage(v))
    }

    // :: LocalDate

    @JvmStatic
    @JvmOverloads
    fun asLocalDate(v: Any?, defaultValue: LocalDate? = null): LocalDate? = when (v) {
        null -> defaultValue
        is LocalDate -> v
        is java.sql.Timestamp -> v.toLocalDateTime().toLocalDate()
        is java.sql.Date -> v.toLocalDate()
        is java.util.Date -> localDateOf(v)
        is LocalDateTime -> v.toLocalDate()
        is OffsetDateTime -> v.toLocalDate()
        is ZonedDateTime -> v.toLocalDate()
        is TemporalAccessor -> LocalDate.from(v)
        is String -> {
            if (v.isEmpty()) defaultValue
            else toLocalDate(parseFlexibleTemporal(v))
        }
        else -> throw IllegalArgumentException(errorMessage(v))
    }

    // :: LocalDateTime

    @JvmStatic
    @JvmOverloads
    fun asLocalDateTime(v: Any?, defaultValue: LocalDateTime? = null): LocalDateTime? = when (v) {
        null -> defaultValue
        is LocalDateTime -> v
        is java.sql.Timestamp -> v.toLocalDateTime()
        is java.sql.Date -> LocalDateTime.of(v.toLocalDate(), LocalTime.MIN)
        is java.util.Date -> localDateTimeOf(v)
        is LocalDate -> v.atStartOfDay()
        is OffsetDateTime -> v.toLocalDateTime()
        is ZonedDateTime -> v.toLocalDateTime()
        is TemporalAccessor -> LocalDateTime.from(v)
        is String -> {
            if (v.isEmpty()) defaultValue
            else toLocalDateTime(parseFlexibleTemporal(v))
        }
        else -> throw IllegalArgumentException(errorMessage(v))
    }

    // :: LocalTime

    @JvmStatic
    @JvmOverloads
    fun asLocalTime(v: Any?, defaultValue: LocalTime? = null): LocalTime? = when (v) {
        null -> defaultValue
        is LocalTime -> v
        is java.sql.Timestamp -> v.toLocalDateTime().toLocalTime()
        is java.util.Date -> localTimeOf(v)
        is OffsetDateTime -> v.toLocalTime()
        is ZonedDateTime -> v.toLocalTime()
        is TemporalAccessor -> LocalTime.from(v)
        is String -> {
            if (v.isEmpty()) defaultValue
            else LocalTime.parse(v)
        }
        else -> throw IllegalArgumentException(errorMessage(v))
    }

    // :: OffsetDateTime

    @JvmStatic
    @JvmOverloads
    fun asOffsetDateTime(v: Any?, defaultValue: OffsetDateTime? = null): OffsetDateTime? = when (v) {
        null -> defaultValue
        is OffsetDateTime -> v
        is java.sql.Timestamp -> v.toLocalDateTime().atOffset(DateUtil.sysZoneOffset)
        is java.sql.Date -> v.toLocalDate().atTime(LocalTime.MIN).atOffset(DateUtil.sysZoneOffset)
        is java.util.Date -> localDateTimeOf(v).atOffset(DateUtil.sysZoneOffset)
        is LocalDateTime -> v.atOffset(DateUtil.sysZoneOffset)
        is LocalDate -> v.atTime(OffsetTime.of(LocalTime.MIN, DateUtil.sysZoneOffset))
        is ZonedDateTime -> v.toOffsetDateTime()
        is TemporalAccessor -> OffsetDateTime.from(v)
        is String -> {
            if (v.isEmpty()) defaultValue
            else toOffsetDateTime(parseFlexibleTemporal(v))
        }
        else -> throw IllegalArgumentException(errorMessage(v))
    }

    // :: ZonedDateTime

    @JvmStatic
    @JvmOverloads
    fun asZonedDateTime(v: Any?, defaultValue: ZonedDateTime? = null): ZonedDateTime? = when (v) {
        null -> defaultValue
        is ZonedDateTime -> v
        is java.sql.Timestamp -> v.toLocalDateTime().atZone(DateUtil.sysZoneOffset)
        is java.sql.Date -> ZonedDateTime.of(v.toLocalDate(), LocalTime.of(0, 0), DateUtil.sysZoneOffset)
        is java.util.Date -> localDateTimeOf(v).atZone(DateUtil.sysZoneOffset)
        is LocalDate -> v.atStartOfDay(DateUtil.sysZoneOffset)
        is LocalDateTime -> v.atZone(DateUtil.sysZoneOffset)
        is OffsetDateTime -> v.toZonedDateTime()
        is TemporalAccessor -> ZonedDateTime.from(v)
        is String -> {
            if (v.isEmpty()) defaultValue
            else toZonedDateTime(parseFlexibleTemporal(v))
        }
        else -> throw IllegalArgumentException(errorMessage(v))
    }

    // :: ByteArray

    @JvmStatic
    @JvmOverloads
    fun asByteArray(v: Any?, defaultValue: ByteArray? = null): ByteArray? = when (v) {
        null -> defaultValue
        is ByteArray -> v
        is Byte -> byteArrayOf(v)
        is String -> if (v.isEmpty()) defaultValue else Base64.getDecoder().decode(v)
        else -> throw IllegalArgumentException(errorMessage(v))
    }

    @JvmStatic
    @JvmOverloads
    fun asByteArrayFromHex(v: Any?, defaultValue: ByteArray? = null): ByteArray? = when (v) {
        null -> defaultValue
        is ByteArray -> v
        is Byte -> byteArrayOf(v)
        is String -> if (v.isEmpty()) defaultValue else HexFormat.of().parseHex(v)
        else -> throw IllegalArgumentException(errorMessage(v))
    }

    // :: Internal helpers

    private fun errorMessage(v: Any): String = "The value cannot be parsed to: '${v.javaClass}'"

    private fun localDateOf(dt: java.util.Date): LocalDate =
        java.sql.Date(dt.time).toLocalDate()

    private fun localDateTimeOf(dt: java.util.Date): LocalDateTime =
        java.sql.Timestamp(dt.time).toLocalDateTime()

    private fun localTimeOf(dt: java.util.Date): LocalTime =
        java.sql.Timestamp(dt.time).toLocalDateTime().toLocalTime()

    private val DATETIME_SPACE_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun parseFlexibleTemporal(str: String): TemporalAccessor {
        val len = str.length

        if (len == 10) return LocalDate.parse(str)

        val hasT = 'T' in str

        if (!hasT && len >= 19 && str[10] == ' ') {
            return LocalDateTime.parse(str, DATETIME_SPACE_FORMAT)
        }

        if (hasT) {
            if ('[' in str) return ZonedDateTime.parse(str)
            if (str[len - 1] == 'Z') return Instant.parse(str)

            val afterT = str.indexOf('T') + 1
            val plusIdx = str.indexOf('+', afterT)
            val minusIdx = str.indexOf('-', afterT)

            if (plusIdx >= 0 || minusIdx >= 0) return OffsetDateTime.parse(str)

            return LocalDateTime.parse(str)
        }

        return parseFlexibleTemporalFallback(str)
    }

    private fun parseFlexibleTemporalFallback(str: String): TemporalAccessor {
        var last: DateTimeParseException? = null

        try { return Instant.parse(str) } catch (e: DateTimeParseException) { last = e }
        try { return OffsetDateTime.parse(str) } catch (_: DateTimeParseException) { }
        try { return LocalDateTime.parse(str) } catch (_: DateTimeParseException) { }
        try { return LocalDate.parse(str) } catch (_: DateTimeParseException) { }

        throw IllegalArgumentException("Cannot parse date/time string: '$str'", last)
    }

    private fun toLocalDate(parsed: TemporalAccessor): LocalDate = when (parsed) {
        is Instant -> parsed.atOffset(DateUtil.sysZoneOffset).toLocalDate()
        is OffsetDateTime -> parsed.toLocalDate()
        is ZonedDateTime -> parsed.toLocalDate()
        is LocalDateTime -> parsed.toLocalDate()
        is LocalDate -> parsed
        else -> LocalDate.from(parsed)
    }

    private fun toLocalDateTime(parsed: TemporalAccessor): LocalDateTime = when (parsed) {
        is Instant -> parsed.atOffset(DateUtil.sysZoneOffset).toLocalDateTime()
        is OffsetDateTime -> parsed.toLocalDateTime()
        is ZonedDateTime -> parsed.toLocalDateTime()
        is LocalDateTime -> parsed
        is LocalDate -> parsed.atStartOfDay()
        else -> LocalDateTime.from(parsed)
    }

    private fun toOffsetDateTime(parsed: TemporalAccessor): OffsetDateTime = when (parsed) {
        is Instant -> parsed.atOffset(DateUtil.sysZoneOffset)
        is OffsetDateTime -> parsed
        is ZonedDateTime -> parsed.toOffsetDateTime()
        is LocalDateTime -> parsed.atOffset(DateUtil.sysZoneOffset)
        is LocalDate -> parsed.atTime(OffsetTime.of(LocalTime.MIN, DateUtil.sysZoneOffset))
        else -> OffsetDateTime.from(parsed)
    }

    private fun toZonedDateTime(parsed: TemporalAccessor): ZonedDateTime = when (parsed) {
        is Instant -> parsed.atZone(DateUtil.sysZoneOffset)
        is OffsetDateTime -> parsed.toZonedDateTime()
        is ZonedDateTime -> parsed
        is LocalDateTime -> parsed.atZone(DateUtil.sysZoneOffset)
        is LocalDate -> parsed.atStartOfDay(DateUtil.sysZoneOffset)
        else -> ZonedDateTime.from(parsed)
    }

    private fun toInstant(parsed: TemporalAccessor): Instant = when (parsed) {
        is Instant -> parsed
        is OffsetDateTime -> parsed.toInstant()
        is ZonedDateTime -> parsed.toInstant()
        is LocalDateTime -> parsed.toInstant(DateUtil.sysZoneOffset)
        is LocalDate -> parsed.atStartOfDay().toInstant(DateUtil.sysZoneOffset)
        else -> Instant.from(parsed)
    }
}
