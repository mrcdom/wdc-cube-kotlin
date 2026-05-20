package br.com.wdc.shopping.view.react

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.shopping.view.react.skeleton.viewimpl.ApplicationReactImpl
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object ViewFlushScheduler {

    private val LOG = Log.getLogger("ViewFlushScheduler")

    private const val FLUSH_INTERVAL_MS = 50L

    private var timer: ScheduledExecutorService? = null

    private val dirtyApps = ConcurrentHashMap<String, ApplicationReactImpl>()

    fun start() {
        if (timer != null) return

        val executor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("view-flush-scheduler").factory()
        )

        executor.scheduleAtFixedRate(::sweep, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS)
        timer = executor

        LOG.info("Started with interval={}ms", FLUSH_INTERVAL_MS)
    }

    fun stop() {
        timer?.shutdownNow()
        timer = null
        dirtyApps.clear()
        LOG.info("Stopped")
    }

    fun markDirty(app: ApplicationReactImpl) {
        dirtyApps[app.id] = app
    }

    fun removeDirty(appId: String) {
        dirtyApps.remove(appId)
    }

    private fun sweep() {
        if (dirtyApps.isEmpty()) return

        val snapshot = ArrayList(dirtyApps.values)
        dirtyApps.clear()

        for (app in snapshot) {
            try {
                app.flushDirtyViews()
            } catch (e: Exception) {
                LOG.error("Error flushing dirty views for app", e)
            }
        }
    }
}
