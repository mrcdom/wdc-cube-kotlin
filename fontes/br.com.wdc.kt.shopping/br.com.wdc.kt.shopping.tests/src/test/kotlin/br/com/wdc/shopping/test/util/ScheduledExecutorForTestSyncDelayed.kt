package br.com.wdc.shopping.test.util

import br.com.wdc.framework.commons.function.Registration
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class ScheduledExecutorForTestSyncDelayed : ScheduledExecutorForTest {

    private var sequenceGenerator = 0
    private var lastExecutedSequence = 0
    private val commandMap = ConcurrentHashMap<Int, () -> Unit>()

    override fun execute(command: () -> Unit): Registration {
        val sequenceId = sequenceGenerator++
        commandMap[sequenceId] = command
        return Registration { commandMap.remove(sequenceId) }
    }

    override fun schedule(command: () -> Unit, delay: Duration): Registration = execute(command)

    override fun scheduleAtFixedRate(command: () -> Unit, initialDelay: Duration, period: Duration): Registration = execute(command)

    override fun scheduleWithFixedDelay(command: () -> Unit, initialDelay: Duration, delay: Duration): Registration = execute(command)

    override fun flush() {
        for (i in lastExecutedSequence until sequenceGenerator) {
            lastExecutedSequence = i + 1
            val cmd = commandMap.remove(i)
            cmd?.invoke()
        }
    }

    override fun shutdown() {
        commandMap.clear()
    }
}
