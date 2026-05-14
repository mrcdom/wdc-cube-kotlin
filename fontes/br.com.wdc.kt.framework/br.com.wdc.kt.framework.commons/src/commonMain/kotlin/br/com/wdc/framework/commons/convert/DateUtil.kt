package br.com.wdc.framework.commons.convert

import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
import kotlin.time.Clock

object DateUtil {

    val sysTimeZone: TimeZone by lazy {
        TimeZone.currentSystemDefault()
    }

    val sysUtcOffset: UtcOffset by lazy {
        sysTimeZone.offsetAt(Clock.System.now())
    }
}
