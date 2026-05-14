package br.com.wdc.framework.commons.lang

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant

/**
 * Platform-specific fallback coercions for the common CoerceUtils methods.
 * On JVM, these handle java.time.*, java.util.Date, java.sql.* types.
 * On other platforms, they return null (no platform-specific types to handle).
 */
internal expect fun platformCoerceToLocalDate(v: Any): LocalDate?
internal expect fun platformCoerceToLocalDateTime(v: Any): LocalDateTime?
internal expect fun platformCoerceToInstant(v: Any): Instant?
