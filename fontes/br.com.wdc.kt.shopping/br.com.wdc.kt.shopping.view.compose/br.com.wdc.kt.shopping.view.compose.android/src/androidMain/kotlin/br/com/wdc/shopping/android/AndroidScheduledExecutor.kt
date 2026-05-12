package br.com.wdc.shopping.android

import android.os.Handler
import android.os.Looper
import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.function.Registration
import kotlin.time.Duration

class AndroidScheduledExecutor : ScheduledExecutor {

    private val handler = Handler(Looper.getMainLooper())

    override fun execute(command: () -> Unit): Registration {
        var cancelled = false
        handler.post {
            if (!cancelled) command()
        }
        return Registration { cancelled = true }
    }

    override fun schedule(command: () -> Unit, delay: Duration): Registration {
        var cancelled = false
        val runnable = Runnable {
            if (!cancelled) command()
        }
        handler.postDelayed(runnable, delay.inWholeMilliseconds)
        return Registration {
            cancelled = true
            handler.removeCallbacks(runnable)
        }
    }

    override fun scheduleAtFixedRate(command: () -> Unit, initialDelay: Duration, period: Duration): Registration {
        var cancelled = false
        val periodMs = period.inWholeMilliseconds

        fun scheduleNext() {
            if (cancelled) return
            val runnable = object : Runnable {
                override fun run() {
                    if (!cancelled) {
                        command()
                        handler.postDelayed(this, periodMs)
                    }
                }
            }
            handler.postDelayed(runnable, periodMs)
        }

        val initialRunnable = Runnable {
            if (!cancelled) {
                command()
                scheduleNext()
            }
        }
        handler.postDelayed(initialRunnable, initialDelay.inWholeMilliseconds)
        return Registration { cancelled = true }
    }

    override fun scheduleWithFixedDelay(command: () -> Unit, initialDelay: Duration, delay: Duration): Registration {
        var cancelled = false
        val delayMs = delay.inWholeMilliseconds

        fun scheduleNext() {
            if (cancelled) return
            val runnable = Runnable {
                if (!cancelled) {
                    command()
                    scheduleNext()
                }
            }
            handler.postDelayed(runnable, delayMs)
        }

        val initialRunnable = Runnable {
            if (!cancelled) {
                command()
                scheduleNext()
            }
        }
        handler.postDelayed(initialRunnable, initialDelay.inWholeMilliseconds)
        return Registration { cancelled = true }
    }
}
