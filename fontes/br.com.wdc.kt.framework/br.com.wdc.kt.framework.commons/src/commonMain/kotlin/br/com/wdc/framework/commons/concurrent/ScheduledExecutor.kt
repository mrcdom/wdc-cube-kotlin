package br.com.wdc.framework.commons.concurrent

import br.com.wdc.framework.commons.function.Registration
import br.com.wdc.framework.commons.util.AtomicRef
import kotlin.time.Duration

interface ScheduledExecutor {

    fun execute(command: () -> Unit): Registration

    fun schedule(command: () -> Unit, delay: Duration): Registration

    fun scheduleAtFixedRate(command: () -> Unit, initialDelay: Duration, period: Duration): Registration

    fun scheduleWithFixedDelay(command: () -> Unit, initialDelay: Duration, delay: Duration): Registration

    companion object {
        val BEAN = AtomicRef<ScheduledExecutor>()
    }
}
