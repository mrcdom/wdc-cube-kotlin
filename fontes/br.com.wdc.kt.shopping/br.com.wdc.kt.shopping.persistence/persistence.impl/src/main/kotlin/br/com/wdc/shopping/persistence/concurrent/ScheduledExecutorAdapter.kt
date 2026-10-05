package br.com.wdc.shopping.persistence.concurrent

import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.function.Registration
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

class ScheduledExecutorAdapter(
    private val service: ScheduledExecutorService,
) : ScheduledExecutor {

    override fun execute(command: () -> Unit): Registration {
        val future = service.schedule(command, 0, TimeUnit.MILLISECONDS)
        return Registration { future.cancel(true) }
    }

    override fun schedule(command: () -> Unit, delay: Duration): Registration {
        val future = service.schedule(command, delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        return Registration { future.cancel(true) }
    }

    override fun scheduleAtFixedRate(command: () -> Unit, initialDelay: Duration, period: Duration): Registration {
        val future = service.scheduleAtFixedRate(command, initialDelay.inWholeMilliseconds, period.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        return Registration { future.cancel(true) }
    }

    override fun scheduleWithFixedDelay(command: () -> Unit, initialDelay: Duration, delay: Duration): Registration {
        val future = service.scheduleWithFixedDelay(command, initialDelay.inWholeMilliseconds, delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        return Registration { future.cancel(true) }
    }
}
