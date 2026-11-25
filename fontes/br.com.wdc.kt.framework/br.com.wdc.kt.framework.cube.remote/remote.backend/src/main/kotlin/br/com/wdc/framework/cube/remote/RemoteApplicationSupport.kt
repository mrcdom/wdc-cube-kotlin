package br.com.wdc.framework.cube.remote

import br.com.wdc.framework.commons.gson.JsonExtensibleObjectOutput
import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.CubeApplication
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubeTreePlace
import br.com.wdc.framework.cube.Scope
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.StringWriter
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The work of a remote session, held by its [RemoteApplication]: it is the rendering technology of the remote client.
 *
 * - **Views.** One [RemoteViewImpl] per scope drawn, made the first time a scope that holds it is written; the client
 *   component that paints it is the scope's vid, looked up in [vids] by the scope's class. A view no longer referred to
 *   is released, and its id goes to the client in `releasedViews`.
 * - **The protocol** ([RemoteProtocol]). Each client message ([sendResponse]): the handshake when it carries a
 *   `clientKey` ([RemoteDataSecurity]) and the client's `protocol` version, what the user typed (`Scope.sync`), the
 *   events (`Scope.dispatch`), then the answer — the scopes of the dirty views, the signed address, and the host's
 *   `serverKey` and version after a handshake. Nothing is read before a handshake with a version the host speaks. Changes made outside a
 *   message go out at the registry's next flush ([flushDirtyViews]).
 * - **The history.** The address the client shows carries a signature ([RemoteAppSecurity.sign]); an address
 *   with a wrong one is not followed ([safeGo]).
 * - **The browser tab** ([browser], [RemoteBrowserPresenter]), the top view, around the application's root scope.
 * - **Life.** [lifeSpan] without a message and without a socket, and the registry releases it.
 *
 * @param app the application whose presenters it draws
 * @param vids the vid of each scope class the application shows (the browser's is built in)
 * @param content the application's root scope: what the browser tab shows
 */
class RemoteApplicationSupport(
    val id: String,
    private val app: CubeApplication,
    private val vids: Map<KClass<out Scope>, String>,
    content: Scope,
    private val lifeSpan: Duration = DEFAULT_LIFE_SPAN,
) : RemoteViewHost {

    companion object {
        private val LOG = Log.getLogger("RemoteApplicationSupport")

        /** How long a session lives without a message (each one, or a connected socket, extends it). */
        val DEFAULT_LIFE_SPAN: Duration = 3.minutes
    }

    /** The registry that holds this session; it flushes the changes made outside a client message. */
    @Volatile
    internal var registry: RemoteApplicationRegistry? = null

    /** When this session expires if no message comes (epoch millis). */
    @Volatile
    internal var expireMoment: Long = System.currentTimeMillis() + lifeSpan.inWholeMilliseconds

    /** The session's cipher for sensitive values (the client starts it with its `clientKey`). */
    val dataSecurity = RemoteDataSecurity(id)

    /** The host's public key of the last handshake, until an answer carries it to the client. */
    private var pendingServerKey: String? = null

    /** The client's connection, when there is one. */
    @Volatile
    var wsSession: WebSocketConnection? = null

    private val dirtyViewMap = ConcurrentHashMap<String, RemoteViewImpl<*>>()

    /** The views the client knows, by instance id: where its events go. */
    private val viewMap = ConcurrentHashMap<String, RemoteViewImpl<*>>()

    /** The view of each scope drawn for the client, until it is released. */
    private val scopeViews: MutableMap<Scope, RemoteViewImpl<*>> = Collections.synchronizedMap(IdentityHashMap())

    /** The ids of the views released since the last message: the client drops them. */
    private val releasedViews = ConcurrentLinkedQueue<String>()
    private var lastRequestId: Long = 0L
    private var historyDirty: Boolean = false

    /** The address the client shows, signed: sent with each answer as "uri". */
    @Volatile
    var uri: String? = null
        private set
    private var instanceIdGen: Int = 1

    /** The browser tab: its scope's view is the client's top view ("<vid>:0"). */
    val browser: RemoteBrowserPresenter = RemoteBrowserPresenter(app, this, content)

    /** The browser's view: every other view is asked for by the view that holds its scope. */
    val browserView: RemoteViewImpl<*> = viewOf(browser.scope)

    // :: Life

    /** Gives the session [lifeSpan] more, from now. */
    fun extendLife() {
        expireMoment = System.currentTimeMillis() + lifeSpan.inWholeMilliseconds
    }

    /** Whether the session is past its life at [now] (epoch millis). */
    fun isExpired(now: Long): Boolean = expireMoment < now

    /** Releases the browser's view (and with it every view), the browser, and leaves the registry. */
    fun release() {
        try {
            browserView.release()
            browser.release()
        } finally {
            registry?.forget(this)
            LOG.info("Session released: {}", id)
        }
    }

    // :: Views

    override fun nextInstanceId(): Int = instanceIdGen++

    override fun viewOf(scope: Scope): RemoteViewImpl<*> {
        var made: RemoteViewImpl<*>? = null
        val view = synchronized(scopeViews) {
            scopeViews.getOrPut(scope) {
                newView(scope).also { made = it }
            }
        }
        made?.let {
            viewMap[it.instanceId] = it
            scope.setUpdater(it.updater)
            // the first drawing
            markDirty(it)
        }
        return view
    }

    private fun newView(scope: Scope): RemoteViewImpl<*> {
        if (scope is BrowserScope) return RemoteViewImpl(BrowserScope.VID, this, scope, instanceId = 0)
        val vid = vids[scope::class] ?: throw IllegalStateException("No vid for ${scope::class.simpleName}")
        return RemoteViewImpl(vid, this, scope)
    }

    override fun forgetView(view: RemoteViewImpl<*>) {
        scopeViews.remove(view.scope, view)
        dirtyViewMap.remove(view.instanceId, view)
    }

    override fun removeView(instanceId: String) {
        dirtyViewMap.remove(instanceId)
        if (viewMap.remove(instanceId) != null) releasedViews.add(instanceId)
    }

    override fun markDirty(view: RemoteViewImpl<*>) {
        if (view.released) return
        dirtyViewMap[view.instanceId] = view
        registry?.markDirty(this)
    }

    /** A snapshot of the views alive, for what applies to all of them. */
    fun liveViews(): List<RemoteViewImpl<*>> = synchronized(scopeViews) { scopeViews.values.toList() }

    /** Sends every view again (the client lost track of them: a view it named is gone, or it started over). */
    fun updateAllViews() {
        liveViews().forEach(::markDirty)
    }

    // :: History

    /** The address changed: the next answer carries it, signed. The application's `updateHistory()` calls this. */
    fun updateHistory() {
        historyDirty = true
    }

    private fun doUpdateHistory() {
        if (!historyDirty) return
        val intent = app.newIntent()
        if (intent.place == null) intent.place = CubeTreePlace.ROOT
        intent.setParameter("sign", sign(intent))
        uri = intent.toString()
        historyDirty = false
    }

    /** Navigates to [path] if its signature is the host's; otherwise stays where it is, and the client is corrected. */
    suspend fun safeGo(path: String?) {
        val intent = CubeIntent.parse(path ?: "")
        if (intent.place == null) intent.place = CubeTreePlace.ROOT

        val actualSignature = intent.removeParameter("sign") ?: ""
        if (actualSignature != sign(intent)) {
            updateHistory()
            val current = app.newIntent()
            if (current.place == null) current.place = CubeTreePlace.ROOT
            app.flip(current)
        } else {
            app.flip(intent)
        }
    }

    private fun sign(intent: CubeIntent): String = RemoteAppSecurity.sign(intent.toString())

    // :: Protocol

    /** Runs a client message — the secret, what the user typed, the events — and answers it. */
    @Synchronized
    @Throws(IOException::class)
    fun sendResponse(request: Map<String, Any?>) {
        try {
            if (!acceptProtocol(request)) {
                refuse(request)
                return
            }
            runBlocking { dispatch(request) }
            respond(request)
        } catch (e: Exception) {
            throw IOException("Sending response", e)
        }
    }

    /** Sends the views that changed outside a client message (the registry's flush). */
    @Synchronized
    fun flushDirtyViews() {
        if (dirtyViewMap.isEmpty() || !protocolAccepted) return
        val ws = wsSession ?: return

        val views = takeDirtyViews()
        if (views.isEmpty()) return

        doUpdateHistory()
        ws.sendText(answer(lastRequestId, isPing = false, views))
    }

    /** Whether a handshake with a version this host speaks was accepted: before it, nothing is read. */
    @Volatile
    private var protocolAccepted = false

    /** The answer to the handshake carries the version too. */
    private var pendingProtocol = false

    /**
     * Whether [request] can be read: a handshake (with a `clientKey`) decides by its `protocol`; any other message,
     * by the last handshake.
     */
    private fun acceptProtocol(request: Map<String, Any?>): Boolean {
        val isHandshake = CoerceUtils.asString(request["clientKey"])?.isNotBlank() == true
        if (!isHandshake) return protocolAccepted

        val version = CoerceUtils.asInteger(request["protocol"], null)
        protocolAccepted = version != null && version in RemoteProtocol.SUPPORTED
        if (!protocolAccepted) LOG.warn("Session {}: a client of protocol {} (this host speaks {})", id, version, RemoteProtocol.SUPPORTED)
        pendingProtocol = protocolAccepted
        return protocolAccepted
    }

    /** The answer to a client this host cannot talk to: the versions it speaks. */
    private fun refuse(request: Map<String, Any?>) {
        val ws = wsSession ?: return
        val requestId = CoerceUtils.asLong(request["requestId"], lastRequestId) ?: lastRequestId
        val out = StringWriter()
        val json = JsonExtensibleObjectOutput(JsonWriter(out))
        try {
            json.beginObject()
            json.name("requestId").value(requestId)
            json.name("error").value(RemoteProtocol.UNSUPPORTED)
            json.name("protocols")
            json.beginArray()
            RemoteProtocol.SUPPORTED.forEach { json.value(it.toLong()) }
            json.endArray()
            json.endObject()
        } finally {
            json.flush()
        }
        ws.sendText(out.toString())
    }

    private suspend fun dispatch(request: Map<String, Any?>) {
        CoerceUtils.asString(request["clientKey"])?.takeIf { it.isNotBlank() }?.let { clientKey ->
            pendingServerKey = try {
                dataSecurity.handshake(clientKey)
            } catch (e: Exception) {
                LOG.warn("Session {}: the handshake failed ({})", id, e.message)
                null
            }
        }

        // what the user typed, before the events
        for ((key, value) in request) {
            val view = viewMap[key] ?: continue
            @Suppress("UNCHECKED_CAST")
            val formData = value as? Map<String, Any?> ?: continue
            view.syncClientToServer(formData)
        }

        @Suppress("UNCHECKED_CAST")
        val events = request["event"] as? List<String>
        if (events.isNullOrEmpty()) return

        // the same event more than once in a message runs once, with the count
        val counted = LinkedHashMap<String, Int>(events.size)
        events.forEach { counted[it] = (counted[it] ?: 0) + 1 }

        var viewNotFound = false
        for ((rawEvent, count) in counted) {
            val pos = rawEvent.lastIndexOf(':')
            if (pos == -1) continue
            val viewId = rawEvent.substring(0, pos)
            val view = viewMap[viewId]
            if (view == null) {
                viewNotFound = true
                continue
            }
            try {
                @Suppress("UNCHECKED_CAST")
                val formData = (request[viewId] as? Map<String, Any?>) ?: emptyMap()
                view.submit(rawEvent.substring(pos + 1).toInt(), count, formData)
            } catch (e: Throwable) {
                app.alertUnexpectedError(LOG, "Unexpected error", e)
                view.update()
            }
        }

        // the client named a view it should no longer have: it gets everything again
        if (viewNotFound) updateAllViews()
    }

    private fun respond(request: Map<String, Any?>) {
        val requestId = CoerceUtils.asLong(request["requestId"], lastRequestId) ?: lastRequestId
        lastRequestId = requestId
        val isPing = CoerceUtils.asBoolean(request["ping"], false) == true

        doUpdateHistory()
        val views = takeDirtyViews()

        // a new address alone waits for the next answer
        if (!isPing && views.isEmpty() && releasedViews.isEmpty() && pendingServerKey == null && !pendingProtocol) return

        val ws = wsSession ?: throw IllegalStateException("Missing WebSocket session")
        ws.sendText(answer(requestId, isPing, views))
    }

    private fun takeDirtyViews(): List<RemoteViewImpl<*>> {
        val views = ArrayList<RemoteViewImpl<*>>()
        val iter = dirtyViewMap.entries.iterator()
        while (iter.hasNext()) {
            views.add(iter.next().value)
            iter.remove()
        }
        return views
    }

    private fun answer(requestId: Long, isPing: Boolean, views: List<RemoteViewImpl<*>>): String {
        val out = StringWriter()
        val json = JsonExtensibleObjectOutput(JsonWriter(out))
        try {
            json.beginObject()
            json.name("requestId").value(requestId)
            if (isPing) json.name("ping").value(true)
            pendingServerKey?.let { json.name("serverKey").value(it) }
            pendingServerKey = null
            if (pendingProtocol) json.name("protocol").value(RemoteProtocol.VERSION.toLong())
            pendingProtocol = false
            uri?.let { json.name("uri").value(it) }
            if (views.isNotEmpty()) {
                json.name("scopes")
                json.beginArray()
                writeScopes(json, views)
                json.endArray()
            }
            writeReleasedViews(json)
            json.endObject()
        } finally {
            json.flush()
        }
        return out.toString()
    }

    /**
     * Writes the scopes of [views] and of every view created while writing them (a scope written for the first time
     * creates the views of the scopes inside it), so the client gets a parent and its children in the same message.
     * The presenters compute their derived fields first, once per paint cycle.
     */
    private fun writeScopes(json: ExtensibleObjectOutput, views: List<RemoteViewImpl<*>>) {
        try {
            app.commitComputedState()
        } catch (e: Exception) {
            LOG.error("commitComputedState", e)
        }
        val written = HashSet<String>()
        var pending = views
        while (pending.isNotEmpty()) {
            for (view in pending) {
                // a view released while an earlier one was written is skipped
                if (!view.released && written.add(view.instanceId)) view.writeScope(json)
            }
            pending = dirtyViewMap.values.filter { it.instanceId !in written }
            pending.forEach { dirtyViewMap.remove(it.instanceId) }
        }
    }

    /** The views released since the last message, so the client drops them (after the scopes that let go of them). */
    private fun writeReleasedViews(json: ExtensibleObjectOutput) {
        if (releasedViews.isEmpty()) return
        json.name("releasedViews")
        json.beginArray()
        while (true) json.value(releasedViews.poll() ?: break)
        json.endArray()
    }
}
