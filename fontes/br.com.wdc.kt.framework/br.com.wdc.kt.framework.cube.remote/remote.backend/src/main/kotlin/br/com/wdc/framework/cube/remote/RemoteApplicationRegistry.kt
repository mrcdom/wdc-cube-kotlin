package br.com.wdc.framework.cube.remote

import br.com.wdc.framework.commons.log.Log
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
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

    private val createdListeners = CopyOnWriteArrayList<(id: String) -> Unit>()

    fun get(id: String?): RemoteApplication? = if (id.isNullOrBlank()) null else apps[id]

    val activeSessionCount: Int get() = apps.size

    /**
     * The session [id], made on its first message ([request]): the browser's start event says where the client is
     * (`p.path`), and the application goes there before answering.
     */
    fun getOrCreate(id: String, request: Map<String, Any?>): RemoteApplication {
        var created = false
        val app = apps.computeIfAbsent(id) { create(id, request).also { created = true } }
        if (created) {
            for (listener in createdListeners) {
                try {
                    listener(id)
                } catch (e: Exception) {
                    LOG.error("Telling that session {} was created", id, e)
                }
            }
        }
        return app
    }

    /**
     * Calls [listener] with the id of each session made here ([getOrCreate]). With several instances, a session made
     * here under an id another instance knows means the client moved here: that one can let go of its own
     * ([releaseIdle]). Closing the result stops it.
     */
    fun onCreated(listener: (id: String) -> Unit): AutoCloseable {
        createdListeners += listener
        return AutoCloseable { createdListeners -= listener }
    }

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

    /**
     * Releases the session [id] if it is here and has no socket: its client went on elsewhere. One with a socket is
     * kept — its client is still here. `true` if it was released.
     */
    fun releaseIdle(id: String): Boolean {
        val app = apps[id] ?: return false
        if (app.remote.wsSession != null) return false
        app.release()
        return true
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
