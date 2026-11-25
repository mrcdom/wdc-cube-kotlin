package br.com.wdc.framework.cube.remote

import br.com.wdc.framework.commons.log.Log
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The remote sessions alive, by id: creates each one with [factory] on the client's first message, releases the ones
 * past their life ([startCleanup]), and sends what changed in a session outside a client message ([startFlushing]).
 *
 * @param factory makes the application of a new session, given its id
 */
class RemoteApplicationRegistry(private val factory: (id: String) -> RemoteApplication) {

    companion object {
        private val LOG = Log.getLogger("RemoteApplicationRegistry")

        /** How often the changes made outside a client message go out. */
        const val FLUSH_INTERVAL_MS = 50L
    }

    private val apps = ConcurrentHashMap<String, RemoteApplication>()

    private val dirty = ConcurrentHashMap<String, RemoteApplicationSupport>()

    private var flusher: ScheduledExecutorService? = null

    fun get(id: String?): RemoteApplication? = if (id.isNullOrBlank()) null else apps[id]

    val activeSessionCount: Int get() = apps.size

    /**
     * The session [id], made on its first message ([request]): the browser's start event says where the client is
     * (`p.path`), and the application goes there before answering.
     */
    fun getOrCreate(id: String, request: Map<String, Any?>): RemoteApplication = apps.computeIfAbsent(id) { create(id, request) }

    private fun create(id: String, request: Map<String, Any?>): RemoteApplication {
        val app = factory(id)
        val session = app.remote
        check(session.id == id) { "The application of session $id says it is ${session.id}" }
        try {
            @Suppress("UNCHECKED_CAST")
            val path = ((request[RemoteBrowserPresenter.VSID] as? Map<String, Any?>)?.get("p.path") as? String)
                ?.takeIf { it.isNotBlank() }
            runBlocking { session.safeGo(path) }
        } catch (caught: Exception) {
            // not registered yet: releasing it does not touch the map being filled
            LOG.error("Starting session {}", id, caught)
            app.release()
            throw caught
        }
        // the first answer goes with the first message; from now on, changes outside a message are flushed
        session.registry = this
        return app
    }

    // :: Life

    /**
     * Runs [removeExpireds] every [intervalSeconds] on [executor]: without it, an application whose client went away
     * (no socket, past its life) would stay in memory for good. Closing the result stops it.
     */
    fun startCleanup(executor: ScheduledExecutorService, intervalSeconds: Long = 30): AutoCloseable {
        val task = executor.scheduleWithFixedDelay(
            {
                try {
                    removeExpireds()
                } catch (e: Exception) {
                    LOG.error("Removing expired applications", e)
                }
            },
            intervalSeconds, intervalSeconds, TimeUnit.SECONDS,
        )
        return AutoCloseable { task.cancel(false) }
    }

    /** Releases the applications past their life with no socket; one with a socket gets more life. */
    fun removeExpireds(now: Long = System.currentTimeMillis()) {
        for (app in apps.values) {
            val session = app.remote
            if (!session.isExpired(now)) continue
            if (session.wsSession == null) app.release() else session.extendLife()
        }
    }

    /** The session was released ([RemoteApplicationSupport.release]). */
    internal fun forget(session: RemoteApplicationSupport) {
        dirty.remove(session.id)
        apps.remove(session.id)
    }

    // :: Flush

    /** Starts sending, every [FLUSH_INTERVAL_MS], what changed in the sessions outside a client message. */
    @Synchronized
    fun startFlushing() {
        if (flusher != null) return
        val executor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("remote-view-flush").factory()
        )
        executor.scheduleAtFixedRate(::sweep, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS)
        flusher = executor
        LOG.info("Flushing every {}ms", FLUSH_INTERVAL_MS)
    }

    @Synchronized
    fun stopFlushing() {
        flusher?.shutdownNow()
        flusher = null
        dirty.clear()
    }

    internal fun markDirty(session: RemoteApplicationSupport) {
        dirty[session.id] = session
    }

    private fun sweep() {
        if (dirty.isEmpty()) return
        val snapshot = ArrayList(dirty.values)
        dirty.clear()
        for (session in snapshot) {
            try {
                session.flushDirtyViews()
            } catch (e: Exception) {
                LOG.error("Flushing session {}", session.id, e)
            }
        }
    }
}
