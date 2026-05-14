package br.com.wdc.framework.commons.lang

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant

private fun java.time.LocalDate.toKtxLocalDate(): LocalDate =
    LocalDate(year, monthValue, dayOfMonth)

private fun java.time.LocalDateTime.toKtxLocalDateTime(): LocalDateTime =
    LocalDateTime(year, monthValue, dayOfMonth, hour, minute, second, nano)

private fun java.time.Instant.toKtInstant(): Instant =
    Instant.fromEpochSeconds(epochSecond, nano.toLong())

internal actual fun platformCoerceToBigInteger(v: Any): BigInteger? = when (v) {
    is java.math.BigInteger -> BigInteger.parseString(v.toString())
    is java.math.BigDecimal -> BigInteger.parseString(v.toBigInteger().toString())
    else -> null
}

internal actual fun platformCoerceToBigDecimal(v: Any): BigDecimal? = when (v) {
    is java.math.BigDecimal -> BigDecimal.parseString(v.toPlainString())
    is java.math.BigInteger -> BigDecimal.parseString(v.toString())
    else -> null
}

internal actual fun platformCoerceToLocalDate(v: Any): LocalDate? = when (v) {
    is java.time.LocalDate -> v.toKtxLocalDate()
    is java.time.LocalDateTime -> v.toLocalDate().toKtxLocalDate()
    is java.time.OffsetDateTime -> v.toLocalDate().toKtxLocalDate()
    is java.time.ZonedDateTime -> v.toLocalDate().toKtxLocalDate()
    is java.time.Instant -> v.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toKtxLocalDate()
    is java.util.Date -> java.time.Instant.ofEpochMilli(v.time).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toKtxLocalDate()
    is java.time.temporal.TemporalAccessor -> java.time.LocalDate.from(v).toKtxLocalDate()
    else -> null
}

internal actual fun platformCoerceToLocalDateTime(v: Any): LocalDateTime? = when (v) {
    is java.time.LocalDateTime -> v.toKtxLocalDateTime()
    is java.time.LocalDate -> java.time.LocalDateTime.of(v, java.time.LocalTime.MIN).toKtxLocalDateTime()
    is java.time.OffsetDateTime -> v.toLocalDateTime().toKtxLocalDateTime()
    is java.time.ZonedDateTime -> v.toLocalDateTime().toKtxLocalDateTime()
    is java.time.Instant -> v.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toKtxLocalDateTime()
    is java.util.Date -> java.time.Instant.ofEpochMilli(v.time).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toKtxLocalDateTime()
    is java.time.temporal.TemporalAccessor -> java.time.LocalDateTime.from(v).toKtxLocalDateTime()
    else -> null
}

internal actual fun platformCoerceToInstant(v: Any): Instant? = when (v) {
    is java.time.Instant -> v.toKtInstant()
    is java.time.OffsetDateTime -> v.toInstant().toKtInstant()
    is java.time.ZonedDateTime -> v.toInstant().toKtInstant()
    is java.time.LocalDateTime -> v.atZone(java.time.ZoneId.systemDefault()).toInstant().toKtInstant()
    is java.time.LocalDate -> v.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toKtInstant()
    is java.util.Date -> java.time.Instant.ofEpochMilli(v.time).toKtInstant()
    is java.time.temporal.TemporalAccessor -> java.time.LocalDateTime.from(v).atZone(java.time.ZoneId.systemDefault()).toInstant().toKtInstant()
    else -> null
}
