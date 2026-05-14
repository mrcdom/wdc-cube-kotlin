package br.com.wdc.framework.commons.lang

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant

internal actual fun platformCoerceToLocalDate(v: Any): LocalDate? = null
internal actual fun platformCoerceToLocalDateTime(v: Any): LocalDateTime? = null
internal actual fun platformCoerceToInstant(v: Any): Instant? = null
