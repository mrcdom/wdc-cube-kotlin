package br.com.wdc.framework.cube.remote.bridge

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CloseEvent
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * The WebSocket exchange: the requests waiting for the host, by request id, sent in order and merged into one message
 * when the socket allows; each answer clears the requests it covers and brings the scopes. Also the keep-alive (event
 * 2 of the browser every 15 s, and when the page comes back into view) and the browser's `submitting` flag (after 200
 * ms of waiting for the user's own request, up to 15 s).
 */
internal class RequestChannel(private val bridge: RemoteBridge) {

    private companion object {
        const val KEEP_ALIVE_INTERVAL = 15_000
        const val SESSION_INVALID = 4001.toShort()
    }

    private var socket: WebSocket? = null
    private val requests = HashMap<Int, dynamic>()
    private var lastSentRequestId = -1
    private var requestCount = 0
    private var lastProcessedId = -1
    private var keepAliveHandler = 0
    private var pendingKeepAlive = 0
    private var submittingTimer = 0
    private var submittingTimeout = 0
    private val userRequestIds = HashSet<Int>()

    /**
     * Whether the hello (with the handshake's `clientKey`) went out on the current socket. Requests wait for it, and
     * then for its answer, which says up to which request the host already has.
     */
    private var helloSent = false

    init {
        val onBack: (dynamic) -> Unit = { if (!document.asDynamic().hidden.unsafeCast<Boolean>()) keepAliveNow() }
        document.addEventListener("visibilitychange", onBack)
        window.addEventListener("focus", onBack)
    }

    /** Queues the form data of [formMap] with the event `<vsid>:<eventId>` and sends what the socket allows. */
    fun submit(formMap: dynamic, vsid: String, eventId: Int, silent: Boolean) {
        cancelPendingKeepAlive()
        val requestId = requestCount++
        formMap.requestId = requestId
        if (formMap.event == undefined) formMap.event = arrayOf<String>()
        formMap.event.push("$vsid:$eventId")
        requests[requestId] = formMap
        if (!silent) userRequestIds += requestId
        resetKeepAliveTimer()
        flush()
    }

    /** Sends, as one message, every request not sent yet. */
    fun flush() {
        val ws = socket ?: return
        if (ws.readyState != WebSocket.OPEN || !helloSent) return

        val message: dynamic = js("({ event: [] })")
        var hasData = false
        for (i in lastSentRequestId + 1 until requestCount) {
            val item = requests[i] ?: continue
            for (key in js("Object.keys")(item).unsafeCast<Array<String>>()) {
                val value = item[key]
                if (value == null || value == undefined || key == "requestId") continue
                if (key == "event") {
                    for (event in value.unsafeCast<Array<String>>()) message.event.push(event)
                } else {
                    if (message[key] == undefined) message[key] = js("({})")
                    js("Object.assign")(message[key], value)
                }
            }
            message.requestId = i
            lastSentRequestId = i
            hasData = true
        }

        if (hasData) {
            ws.send(JSON.stringify(message))
            if (userRequestIds.isNotEmpty()) setSubmitting(true)
        }
    }

    /** Opens the socket to [url], unless it is open or opening. */
    @OptIn(DelicateCoroutinesApi::class)
    fun open(url: String) {
        socket?.let { if (it.readyState == WebSocket.OPEN || it.readyState == WebSocket.CONNECTING) return }

        val ws = WebSocket(url, arrayOf("wdc"))
        socket = ws

        fun disconnected(cause: Any?) {
            socket = null
            bridge.isConnected = false
            stopKeepAliveChecks()
            userRequestIds.clear()
            setSubmitting(false)
            bridge.reconnect.reconnect(cause)
        }

        helloSent = false
        ws.onopen = {
            bridge.isConnected = true
            initKeepAliveChecks()
            keepAliveNow()
            // the hello goes first, with this connection's public key: the host answers with its own
            GlobalScope.launch {
                val clientKey = bridge.dataSecurity.startHandshake()
                if (socket === ws && ws.readyState == WebSocket.OPEN) {
                    val hello: dynamic = js("({ ping: true })")
                    hello.requestId = lastProcessedId
                    hello.path = bridge.path
                    hello.clientKey = clientKey
                    ws.send(JSON.stringify(hello))
                    // the queued requests go with the answer: the host says there which ones it already has
                    helloSent = true
                }
            }
        }
        ws.onerror = { disconnected(it) }
        ws.onclose = { event ->
            // the host says the session is not valid: a new page starts a new one
            if ((event as CloseEvent).code == SESSION_INVALID) window.location.reload() else disconnected(event)
        }
        ws.onmessage = { event -> receive(JSON.parse((event as MessageEvent).data as String)) }
    }

    private fun receive(response: dynamic) {
        if (bridge.reconnect.count > 0) bridge.reconnect.reset()

        val serverKey = response.serverKey
        if (serverKey != null && serverKey != undefined) {
            GlobalScope.launch {
                try {
                    bridge.dataSecurity.completeHandshake(serverKey as String, bridge.id)
                } catch (e: Throwable) {
                    console.error("Remote bridge: the handshake failed", e)
                }
            }
        }

        val requestId = (response.requestId as Number).toInt()
        if (response.ping == true) {
            lastSentRequestId = requestId
            lastProcessedId = requestId
        }
        for (i in lastProcessedId + 1..requestId) {
            requests.remove(i)
            userRequestIds -= i
            lastProcessedId = i
        }

        val uri = response.uri
        if (uri != null && uri != undefined) {
            bridge.path = uri as String
            window.location.href = "#$uri"
        }
        if (response.scopes != undefined) bridge.applyScopes(response.scopes.unsafeCast<Array<dynamic>>())
        // after the scopes that let go of them
        if (response.releasedViews != undefined) {
            bridge.collector.released(response.releasedViews.unsafeCast<Array<String>>().toList())
        }

        flush()
        setSubmitting(userRequestIds.isNotEmpty())
    }

    private fun initKeepAliveChecks() {
        stopKeepAliveChecks()
        keepAliveHandler = window.setTimeout({ keepAlive() }, KEEP_ALIVE_INTERVAL)
    }

    private fun stopKeepAliveChecks() {
        window.clearTimeout(keepAliveHandler)
        keepAliveHandler = 0
    }

    private fun keepAliveNow() {
        cancelPendingKeepAlive()
        if (socket?.readyState == WebSocket.OPEN) {
            pendingKeepAlive = window.setTimeout({
                pendingKeepAlive = 0
                bridge.keepAlive()
            }, 80)
        }
    }

    private fun cancelPendingKeepAlive() {
        if (pendingKeepAlive != 0) {
            window.clearTimeout(pendingKeepAlive)
            pendingKeepAlive = 0
        }
    }

    private fun resetKeepAliveTimer() {
        if (keepAliveHandler != 0) {
            stopKeepAliveChecks()
            keepAliveHandler = window.setTimeout({ keepAlive() }, KEEP_ALIVE_INTERVAL)
        }
    }

    private fun keepAlive() {
        stopKeepAliveChecks()
        keepAliveNow()
        keepAliveHandler = window.setTimeout({ keepAlive() }, KEEP_ALIVE_INTERVAL)
    }

    private fun setSubmitting(value: Boolean) {
        if (value) {
            if (submittingTimer == 0) {
                submittingTimer = window.setTimeout({
                    submittingTimer = 0
                    applySubmitting(true)
                }, 200)
            }
            if (submittingTimeout == 0) {
                submittingTimeout = window.setTimeout({
                    submittingTimeout = 0
                    setSubmitting(false)
                }, 15_000)
            }
        } else {
            window.clearTimeout(submittingTimer)
            submittingTimer = 0
            window.clearTimeout(submittingTimeout)
            submittingTimeout = 0
            applySubmitting(false)
        }
    }

    private fun applySubmitting(value: Boolean) {
        val browser = bridge.viewMap[RemoteBridge.BROWSER_VSID] ?: return
        if (browser.scope.boolean("submitting") != value) browser.set("submitting", value)
    }
}
