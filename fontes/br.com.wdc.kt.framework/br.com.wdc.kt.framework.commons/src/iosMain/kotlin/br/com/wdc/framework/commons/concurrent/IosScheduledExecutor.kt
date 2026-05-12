package br.com.wdc.framework.commons.concurrent

import br.com.wdc.framework.commons.function.Registration
import platform.darwin.*
import kotlin.time.Duration

/**
 * ScheduledExecutor implementation for iOS using Grand Central Dispatch.
 */
class IosScheduledExecutor : ScheduledExecutor {

    private val queue = dispatch_get_main_queue()

    override fun execute(command: () -> Unit): Registration {
        var cancelled = false
        dispatch_async(queue) {
            if (!cancelled) command()
        }
        return Registration { cancelled = true }
    }

    override fun schedule(command: () -> Unit, delay: Duration): Registration {
        var cancelled = false
        val delayNanos = delay.inWholeNanoseconds
        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, delayNanos),
            queue
        ) {
            if (!cancelled) command()
        }
        return Registration { cancelled = true }
    }

    override fun scheduleAtFixedRate(command: () -> Unit, initialDelay: Duration, period: Duration): Registration {
        var cancelled = false
        val periodNanos = period.inWholeNanoseconds

        fun scheduleNext() {
            if (cancelled) return
            dispatch_after(
                dispatch_time(DISPATCH_TIME_NOW, periodNanos),
                queue
            ) {
                if (!cancelled) {
                    command()
                    scheduleNext()
                }
            }
        }

        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, initialDelay.inWholeNanoseconds),
            queue
        ) {
            if (!cancelled) {
                command()
                scheduleNext()
            }
        }
        return Registration { cancelled = true }
    }

    override fun scheduleWithFixedDelay(command: () -> Unit, initialDelay: Duration, delay: Duration): Registration {
        var cancelled = false
        val delayNanos = delay.inWholeNanoseconds

        fun scheduleNext() {
            if (cancelled) return
            dispatch_after(
                dispatch_time(DISPATCH_TIME_NOW, delayNanos),
                queue
            ) {
                if (!cancelled) {
                    command()
                    scheduleNext()
                }
            }
        }

        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, initialDelay.inWholeNanoseconds),
            queue
        ) {
            if (!cancelled) {
                command()
                scheduleNext()
            }
        }
        return Registration { cancelled = true }
    }
}
