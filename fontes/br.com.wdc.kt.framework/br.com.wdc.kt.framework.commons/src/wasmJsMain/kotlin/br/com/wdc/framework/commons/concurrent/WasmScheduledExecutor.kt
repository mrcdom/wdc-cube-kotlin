package br.com.wdc.framework.commons.concurrent

import br.com.wdc.framework.commons.function.Registration
import kotlin.time.Duration

/**
 * ScheduledExecutor implementation for wasmJs using browser setTimeout/setInterval.
 */
class WasmScheduledExecutor : ScheduledExecutor {

    override fun execute(command: () -> Unit): Registration {
        val id = setTimeout(command, 0)
        return Registration { clearTimeout(id) }
    }

    override fun schedule(command: () -> Unit, delay: Duration): Registration {
        val id = setTimeout(command, delay.inWholeMilliseconds.toInt())
        return Registration { clearTimeout(id) }
    }

    override fun scheduleAtFixedRate(command: () -> Unit, initialDelay: Duration, period: Duration): Registration {
        var intervalId: Int? = null
        val timeoutId = setTimeout({
            command()
            intervalId = setInterval(command, period.inWholeMilliseconds.toInt())
        }, initialDelay.inWholeMilliseconds.toInt())
        return Registration {
            clearTimeout(timeoutId)
            intervalId?.let { clearInterval(it) }
        }
    }

    override fun scheduleWithFixedDelay(command: () -> Unit, initialDelay: Duration, delay: Duration): Registration {
        var cancelled = false
        fun scheduleNext() {
            if (!cancelled) {
                setTimeout({
                    if (!cancelled) {
                        command()
                        scheduleNext()
                    }
                }, delay.inWholeMilliseconds.toInt())
            }
        }
        val timeoutId = setTimeout({
            if (!cancelled) {
                command()
                scheduleNext()
            }
        }, initialDelay.inWholeMilliseconds.toInt())
        return Registration {
            cancelled = true
            clearTimeout(timeoutId)
        }
    }
}

// JS interop for browser timers
@JsFun("(callback, ms) => setTimeout(callback, ms)")
private external fun setTimeout(callback: () -> Unit, ms: Int): Int

@JsFun("(id) => clearTimeout(id)")
private external fun clearTimeout(id: Int)

@JsFun("(callback, ms) => setInterval(callback, ms)")
private external fun setInterval(callback: () -> Unit, ms: Int): Int

@JsFun("(id) => clearInterval(id)")
private external fun clearInterval(id: Int)
