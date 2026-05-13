package br.com.wdc.shopping.test.util

import br.com.wdc.framework.commons.function.Registration
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

class ScheduledExecutorForTestAsync : ScheduledExecutorForTest {

    private val timer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().daemon(true).name("test-async-timer").factory()
    )

    private val vtExecutor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    @Volatile
    private var pendingFutures: MutableList<Future<*>> = Collections.synchronizedList(ArrayList())

    @Volatile
    private var running = true

    override fun shutdown() {
        running = false
        timer.shutdownNow()
        vtExecutor.shutdownNow()
    }

    override fun flush() {
        if (!running) return

        val batch = pendingFutures
        pendingFutures = Collections.synchronizedList(ArrayList())

        for (future in batch) {
            future.get()
        }
    }

    override fun execute(command: () -> Unit): Registration {
        val allowed = AtomicBoolean(true)
        val future = vtExecutor.submit<Void?> {
            if (allowed.get() && running) {
                command()
            }
            null
        }
        pendingFutures.add(future)
        return Registration { allowed.set(false) }
    }

    override fun schedule(command: () -> Unit, delay: Duration): Registration {
        val allowed = AtomicBoolean(true)
        val future = vtExecutor.submit<Void?> {
            if (allowed.get() && running) {
                Thread.sleep(delay.inWholeMilliseconds)
                command()
            }
            null
        }
        pendingFutures.add(future)
        return Registration { allowed.set(false) }
    }

    @Suppress("UNCHECKED_CAST")
    override fun scheduleAtFixedRate(command: () -> Unit, initialDelay: Duration, period: Duration): Registration {
        val allowed = AtomicBoolean(true)

        val future = timer.scheduleAtFixedRate(
            {
                if (!running || !allowed.get()) return@scheduleAtFixedRate

                Thread.startVirtualThread {
                    try {
                        if (running && allowed.get()) {
                            command()
                        }
                    } catch (_: Throwable) {
                        // periodic task failures are intentionally swallowed
                    }
                }
            },
            initialDelay.inWholeMilliseconds,
            period.inWholeMilliseconds,
            TimeUnit.MILLISECONDS
        )

        return Registration {
            allowed.set(false)
            future.cancel(false)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun scheduleWithFixedDelay(command: () -> Unit, initialDelay: Duration, delay: Duration): Registration {
        val allowed = AtomicBoolean(true)

        val future = timer.scheduleWithFixedDelay(
            {
                if (!running || !allowed.get()) return@scheduleWithFixedDelay

                Thread.startVirtualThread {
                    try {
                        if (running && allowed.get()) {
                            command()
                        }
                    } catch (_: Throwable) {
                        // periodic task failures are intentionally swallowed
                    }
                }
            },
            initialDelay.inWholeMilliseconds,
            delay.inWholeMilliseconds,
            TimeUnit.MILLISECONDS
        )

        return Registration {
            allowed.set(false)
            future.cancel(false)
        }
    }
}
