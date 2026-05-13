package br.com.wdc.shopping.test.util

import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.function.Registration
import java.time.Duration

interface ScheduledExecutorForTest : ScheduledExecutor {

    @Throws(Exception::class)
    fun flush()

    fun shutdown()
}
