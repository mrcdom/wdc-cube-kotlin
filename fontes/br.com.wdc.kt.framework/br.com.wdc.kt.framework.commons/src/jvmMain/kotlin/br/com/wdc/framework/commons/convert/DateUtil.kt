package br.com.wdc.framework.commons.convert

import java.time.ZoneOffset

/**
 * JVM extension: provides java.time.ZoneOffset derived from the commonMain DateUtil.sysUtcOffset.
 */
val DateUtil.sysZoneOffset: ZoneOffset
    get() = ZoneOffset.ofTotalSeconds(sysUtcOffset.totalSeconds)

