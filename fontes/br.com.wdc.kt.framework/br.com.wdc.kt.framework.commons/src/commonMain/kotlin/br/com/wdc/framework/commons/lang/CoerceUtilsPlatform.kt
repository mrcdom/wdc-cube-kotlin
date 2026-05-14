package br.com.wdc.framework.commons.lang

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant

/**
 * Platform-specific fallback coercions for the common CoerceUtils methods.
 * On JVM, these handle java.time.*, java.util.Date, java.sql.*, java.math.* types.
 * On other platforms, they return null (no platform-specific types to handle).
 */
internal expect fun platformCoerceToBigInteger(v: Any): BigInteger?
internal expect fun platformCoerceToBigDecimal(v: Any): BigDecimal?
internal expect fun platformCoerceToLocalDate(v: Any): LocalDate?
internal expect fun platformCoerceToLocalDateTime(v: Any): LocalDateTime?
internal expect fun platformCoerceToInstant(v: Any): Instant?
