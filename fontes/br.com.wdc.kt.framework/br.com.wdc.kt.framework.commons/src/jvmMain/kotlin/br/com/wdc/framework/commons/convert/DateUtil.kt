package br.com.wdc.framework.commons.convert

import java.time.ZoneOffset
import java.util.TimeZone

object DateUtil {

    val sysZoneOffset: ZoneOffset by lazy {
        val tz = TimeZone.getDefault()
        ZoneOffset.ofTotalSeconds(tz.getOffset(System.currentTimeMillis()) / 1000)
    }

    val sysTimeZone: TimeZone by lazy {
        TimeZone.getDefault()
    }
}
