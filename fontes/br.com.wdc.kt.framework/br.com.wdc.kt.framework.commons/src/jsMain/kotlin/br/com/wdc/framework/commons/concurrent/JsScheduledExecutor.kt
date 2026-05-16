package br.com.wdc.framework.commons.concurrent

import br.com.wdc.framework.commons.function.Registration
import kotlinx.browser.window
import kotlin.time.Duration

/**
 * ScheduledExecutor implementation for Kotlin/JS using browser setTimeout/setInterval.
 */
class JsScheduledExecutor : ScheduledExecutor {

    override fun execute(command: () -> Unit): Registration {
        val id = window.setTimeout(command, 0)
        return Registration { window.clearTimeout(id) }
    }

    override fun schedule(command: () -> Unit, delay: Duration): Registration {
        val id = window.setTimeout(command, delay.inWholeMilliseconds.toInt())
        return Registration { window.clearTimeout(id) }
    }

    override fun scheduleAtFixedRate(command: () -> Unit, initialDelay: Duration, period: Duration): Registration {
        var intervalId: Int? = null
        val timeoutId = window.setTimeout({
            command()
            intervalId = window.setInterval(command, period.inWholeMilliseconds.toInt())
        }, initialDelay.inWholeMilliseconds.toInt())
        return Registration {
            window.clearTimeout(timeoutId)
            intervalId?.let { window.clearInterval(it) }
        }
    }

    override fun scheduleWithFixedDelay(command: () -> Unit, initialDelay: Duration, delay: Duration): Registration {
        var cancelled = false
        fun scheduleNext() {
            if (!cancelled) {
                window.setTimeout({
                    if (!cancelled) {
                        command()
                        scheduleNext()
                    }
                }, delay.inWholeMilliseconds.toInt())
            }
        }
        val timeoutId = window.setTimeout({
            if (!cancelled) {
                command()
                scheduleNext()
            }
        }, initialDelay.inWholeMilliseconds.toInt())
        return Registration {
            cancelled = true
            window.clearTimeout(timeoutId)
        }
    }
}
