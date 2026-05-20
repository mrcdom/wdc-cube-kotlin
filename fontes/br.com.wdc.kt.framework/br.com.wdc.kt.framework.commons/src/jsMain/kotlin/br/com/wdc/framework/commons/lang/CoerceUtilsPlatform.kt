package br.com.wdc.framework.commons.lang

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant

internal actual fun platformCoerceToBigInteger(v: Any): BigInteger? = null
internal actual fun platformCoerceToBigDecimal(v: Any): BigDecimal? = null
internal actual fun platformCoerceToLocalDate(v: Any): LocalDate? = null
internal actual fun platformCoerceToLocalDateTime(v: Any): LocalDateTime? = null
internal actual fun platformCoerceToInstant(v: Any): Instant? = null
