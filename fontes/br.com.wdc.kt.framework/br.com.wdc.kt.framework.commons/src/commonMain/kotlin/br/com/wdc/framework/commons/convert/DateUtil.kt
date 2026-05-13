package br.com.wdc.framework.commons.convert

import kotlinx.datetime.*
import kotlin.time.Clock

object DateUtil {

    val sysTimeZone: TimeZone by lazy {
        TimeZone.currentSystemDefault()
    }

    val sysUtcOffset: UtcOffset by lazy {
        sysTimeZone.offsetAt(Clock.System.now())
    }
}
