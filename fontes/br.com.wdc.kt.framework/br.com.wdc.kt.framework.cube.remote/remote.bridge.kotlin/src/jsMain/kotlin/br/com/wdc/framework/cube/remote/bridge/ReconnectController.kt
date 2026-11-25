package br.com.wdc.framework.cube.remote.bridge

import kotlinx.browser.window

/**
 * Reconnects after the socket drops: waits `min(2000 · n, 120000)` ms before attempt n, counting down on the
 * browser's scope (`error`: `cause`, `numAttempt`, `delay`), which the browser view shows.
 */
internal class ReconnectController(private val bridge: RemoteBridge) {

    val url: String = bridge.baseWebSocketUrl + "/dispatcher/" + bridge.id

    var count = 0
        private set
    private var delay = 0
    private var cause: Any? = null
    private var handler = 0

    fun reconnect(cause: Any?) {
        count++
        delay = minOf(2000 * count, 120_000)
        this.cause = cause
        showError()
        if (handler == 0) handler = window.setInterval({ check() }, 1000)
    }

    fun check() {
        if (bridge.viewMap[RemoteBridge.BROWSER_VSID] == null || bridge.isConnected) {
            reset()
            return
        }
        if (delay > 0) delay = maxOf(0, delay - 1000)
        showError()
        if (delay <= 0) window.setTimeout({ bridge.ensureConnected() }, 16)
    }

    fun reset() {
        bridge.viewMap[RemoteBridge.BROWSER_VSID]?.set("error", undefined)
        count = 0
        delay = 0
        cause = null
        window.clearInterval(handler)
        handler = 0
    }

    fun checkNow() {
        delay = 0
        check()
    }

    private fun showError() {
        val error: dynamic = js("({})")
        error.cause = cause
        error.numAttempt = count
        error.delay = delay
        bridge.viewMap[RemoteBridge.BROWSER_VSID]?.set("error", error)
    }
}
