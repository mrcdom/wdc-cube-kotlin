package br.com.wdc.shopping.test.util

import br.com.wdc.framework.commons.function.Registration
import kotlin.time.Duration

class ScheduledExecutorForTestSyncDirect : ScheduledExecutorForTest {

    override fun execute(command: () -> Unit): Registration {
        command()
        return Registration.noop()
    }

    override fun schedule(command: () -> Unit, delay: Duration): Registration = execute(command)

    override fun scheduleAtFixedRate(command: () -> Unit, initialDelay: Duration, period: Duration): Registration = execute(command)

    override fun scheduleWithFixedDelay(command: () -> Unit, initialDelay: Duration, delay: Duration): Registration = execute(command)

    override fun flush() {
        // NOOP
    }

    override fun shutdown() {
        // NOOP
    }
}
