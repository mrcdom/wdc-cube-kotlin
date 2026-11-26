package br.com.wdc.framework.cube.remote.bridge

import kotlinx.browser.document
import kotlinx.browser.sessionStorage
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * The client side of one remote application (`framework-cube-remote-backend`), in the browser: the session id, the security
 * handshake, the WebSocket exchange, the history, and the scopes of the views the host publishes. One per page.
 *
 * The page comes from the host, which plants the `app_id` cookie (the session id, signed by the host). Each connection
 * agrees on the session's key ([DataSecurity]); [start] connects and asks for the address in the page's hash; from then on
 * the host sends the scopes, which the views read ([scopeOf], [ViewBinding]), and the views send what the user typed
 * ([setFormField]) and the events ([submit]).
 *
 * It uses no UI technology: the React bindings are the module `framework-cube-remote-bridge-react`.
 */
class RemoteBridge {

    companion object {
        /** The browser tab's view: the client's top one. */
        const val BROWSER_VID = "7b32e816a191"
        const val BROWSER_VSID = "$BROWSER_VID:0"

        /** The version of the remote protocol this client speaks: it goes in the handshake (`protocol`). */
        const val PROTOCOL_VERSION = 1

        /** What the user is told when the host does not speak this client's version. */
        const val OUTDATED_MESSAGE = "This page is out of date. Reload it to continue."
    }

    /**
     * Whether the host refused this client's protocol version: the page is out of date. Nothing more is sent; the
     * browser's alert says so, and its OK reloads the page.
     */
    var outdated = false
        private set

    /** The session id. */
    val id: String

    /** The bindings of the views the host published, by instance id. */
    internal val viewMap = HashMap<String, ViewBinding>()

    /** The session's cipher: [cipher] uses it. */
    val dataSecurity = DataSecurity()

    internal val baseWebSocketUrl: String
    internal var isConnected = false

    /** The address the client shows (the hash), as the host last signed it. */
    var path = ""
        internal set

    internal val collector = ViewGarbageCollector(this)
    internal val channel: RequestChannel
    internal val reconnect: ReconnectController

    private var formMap: dynamic = js("({})")
    private var hashListener: ((dynamic) -> Unit)? = null

    init {
        viewMap[BROWSER_VSID] = ViewBinding(BROWSER_VSID)

        val appIdFromCookie = Cookies.get("app_id")?.also { Cookies.remove("app_id") }
        id = sessionStorage.getItem("app_id")
            ?: appIdFromCookie?.also { sessionStorage.setItem("app_id", it) }
            ?: "${Random.nextLong().toString(36)}.fake"

        // the host's routes are at the root, whatever context served the page
        baseWebSocketUrl = (if (window.location.protocol == "http:") "ws://" else "wss://") + window.location.host

        channel = RequestChannel(this)
        reconnect = ReconnectController(this)
    }

    // :: Life

    /** Connects (the handshake runs on each connection) and asks the host for the page's address (the browser's event -1). */
    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        GlobalScope.launch {
            ensureConnected()

            hashListener?.let { window.removeEventListener("hashchange", it) }
            val listener: (dynamic) -> Unit = {
                val newPath = currentHash()
                if (newPath != path) onHistoryChange(newPath)
            }
            hashListener = listener
            window.addEventListener("hashchange", listener)

            path = currentHash()
            setFormField(BROWSER_VSID, "p.path", path)
            submit(BROWSER_VSID, -1)
        }
    }

    fun stop() {
        hashListener?.let { window.removeEventListener("hashchange", it) }
        hashListener = null
    }

    /** Connects now, if not connected. */
    fun connect() = reconnect.checkNow()

    internal fun ensureConnected() = channel.open(reconnect.url)

    internal fun keepAlive() = submit(BROWSER_VSID, 2)

    private fun onHistoryChange(newPath: String) {
        setFormField(BROWSER_VSID, "p.path", newPath)
        submit(BROWSER_VSID, -2)
    }

    private fun currentHash(): String = window.location.hash.let { if (it.length > 1) it.substring(1) else "/" }

    // :: Views

    /** The binding of [vsid] (made if missing): the values of its scope, and who repaints when they change. */
    fun bindingOf(vsid: String): ViewBinding =
        viewMap[vsid] ?: collector.recover(vsid) ?: ViewBinding(vsid).also { viewMap[vsid] = it }

    /** The scope of [vsid] as it is now (empty if the host has not sent it yet). */
    fun scopeOf(vsid: String): RemoteScope = bindingOf(vsid).scope

    /** The view of [vsid] is on screen / left the screen: the collector keeps it, or lets it go later. */
    fun mounted(vsid: String) = collector.mount(vsid)

    fun unmounted(binding: ViewBinding) = collector.mark(binding)

    /** Keeps [vsid] while held (a view that will show it later). */
    fun hold(vsid: String) = collector.hold(vsid)

    fun release(vsid: String) = collector.release(vsid)

    internal fun applyScopes(scopes: Array<dynamic>) {
        for (scope in scopes) {
            val vsid = scope?.id as? String ?: continue
            collector.recover(vsid)
            val binding = viewMap.getOrPut(vsid) { ViewBinding(vsid) }
            binding.setScope(scope)
            collector.updateAutoHolds(binding)
        }
    }

    // :: Events

    /** Sets what the user typed in a field of [vsid]'s form; it goes with the next event. */
    fun setFormField(vsid: String, fieldName: String, fieldValue: Any?) {
        if (formMap[vsid] == undefined) formMap[vsid] = js("({})")
        formMap[vsid][fieldName] = fieldValue
    }

    /** Sends the event [eventId] of [vsid], with the form data set since the last one; the browser's are silent. */
    fun submit(vsid: String, eventId: Int) = send(vsid, eventId, silent = vsid == BROWSER_VSID)

    /** Sends an event that does not show the `submitting` flag. */
    fun submitSilent(vsid: String, eventId: Int) = send(vsid, eventId, silent = true)

    private fun send(vsid: String, eventId: Int, silent: Boolean) {
        if (outdated) {
            // the alert's OK: a new page brings a client the host speaks to
            if (vsid == BROWSER_VSID && eventId == 1) window.location.reload()
            return
        }
        val form = formMap
        formMap = js("({})")
        channel.submit(form, vsid, eventId, silent)
    }

    /**
     * What the user is told when the host does not speak this client's version: [OUTDATED_MESSAGE] (English) unless
     * the application gives it in the user's language.
     */
    var outdatedMessage: String = OUTDATED_MESSAGE

    /** The host does not speak this client's version: stop, and tell the user through the browser's alert. */
    internal fun markOutdated() {
        outdated = true
        val alert: dynamic = js("({ id: -1 })")
        alert.args = arrayOf(outdatedMessage)
        viewMap[BROWSER_VSID]?.set("alertMessage", alert)
    }

    /** Ciphers a sensitive value (a password) with the session's key, for [setFormField]. */
    suspend fun cipher(value: String): String = dataSecurity.b64Cipher(value)
}

/** The page's cookies: the session id the host planted, read once and removed (`path=/`). */
internal object Cookies {

    fun get(name: String): String? = document.cookie.split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("$name=") }
        ?.substringAfter("=")
        ?.let { js("decodeURIComponent")(it) as String }

    fun remove(name: String) {
        document.cookie = "$name=; path=/; max-age=0"
    }
}
