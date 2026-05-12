package br.com.wdc.shopping.desktop

import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.function.Registration
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.time.Duration

class DesktopScheduledExecutor : ScheduledExecutor {

    override fun execute(command: () -> Unit): Registration {
        var cancelled = false
        SwingUtilities.invokeLater { if (!cancelled) command() }
        return Registration { cancelled = true }
    }

    override fun schedule(command: () -> Unit, delay: Duration): Registration {
        var cancelled = false
        val timer = Timer(delay.inWholeMilliseconds.toInt()) { if (!cancelled) command() }
        timer.isRepeats = false
        timer.start()
        return Registration { cancelled = true; timer.stop() }
    }

    override fun scheduleAtFixedRate(command: () -> Unit, initialDelay: Duration, period: Duration): Registration {
        var cancelled = false
        val timer = Timer(period.inWholeMilliseconds.toInt()) { if (!cancelled) command() }
        timer.isRepeats = true
        timer.initialDelay = initialDelay.inWholeMilliseconds.toInt()
        timer.start()
        return Registration { cancelled = true; timer.stop() }
    }

    override fun scheduleWithFixedDelay(command: () -> Unit, initialDelay: Duration, delay: Duration): Registration {
        var cancelled = false
        var currentTimer: Timer? = null

        fun scheduleNext() {
            if (cancelled) return
            val t = Timer(delay.inWholeMilliseconds.toInt()) {
                if (!cancelled) {
                    command()
                    scheduleNext()
                }
            }
            t.isRepeats = false
            currentTimer = t
            t.start()
        }

        val initialTimer = Timer(initialDelay.inWholeMilliseconds.toInt()) {
            if (!cancelled) {
                command()
                scheduleNext()
            }
        }
        initialTimer.isRepeats = false
        currentTimer = initialTimer
        initialTimer.start()

        return Registration { cancelled = true; currentTimer?.stop() }
    }
}
