package br.com.wdc.framework.cube.remote.bridge

import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.promise
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.io.encoding.Base64
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.fail

/**
 * Runs every client fixture of `conformance/client/` against this client, with a fake WebSocket: the runner plays
 * the host, the bridge is the client under test.
 */
class ClientConformanceTest {

    @BeforeTest
    fun installFakeSocket() {
        js(
            """
            globalThis.__sockets = [];
            globalThis.__RealWebSocket = globalThis.__RealWebSocket || globalThis.WebSocket;
            function FakeWebSocket(url, protocols) {
                var me = this;
                this.url = url; this.readyState = 0; this.sent = [];
                globalThis.__sockets.push(this);
                setTimeout(function () { me.readyState = 1; if (me.onopen) me.onopen({}); }, 0);
            }
            FakeWebSocket.prototype.send = function (text) { this.sent.push(text); };
            FakeWebSocket.prototype.close = function () {
                var me = this;
                if (this.readyState === 3) return;
                this.readyState = 3;
                setTimeout(function () { if (me.onclose) me.onclose(new CloseEvent('close', { code: 1000 })); }, 0);
            };
            FakeWebSocket.CONNECTING = 0; FakeWebSocket.OPEN = 1; FakeWebSocket.CLOSING = 2; FakeWebSocket.CLOSED = 3;
            globalThis.WebSocket = FakeWebSocket;
            """,
        )
    }

    @AfterTest
    fun restoreSocket() {
        js("globalThis.WebSocket = globalThis.__RealWebSocket")
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun clientFixtures(): Promise<Unit> = GlobalScope.promise {
        if (CLIENT_FIXTURES.isEmpty()) fail("no client fixtures")
        val failures = ArrayList<String>()
        for ((name, encoded) in CLIENT_FIXTURES) {
            try {
                runFixture(JSON.parse<dynamic>(Base64.decode(encoded).decodeToString()))
            } catch (e: Throwable) {
                failures += "$name: ${e.message}"
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    private suspend fun runFixture(fixture: dynamic) {
        val matcher = FixtureMatcher()
        var bridge: RemoteBridge? = null
        var seen = 0
        val steps = fixture.steps.unsafeCast<Array<dynamic>>()
        for ((index, step) in steps.withIndex()) {
            try {
                when {
                    step.start != undefined -> {
                        window.location.hash = step.start.hash as String
                        js("sessionStorage.removeItem('app_id')")
                        val before = socketCount()
                        bridge = RemoteBridge().also { it.start() }
                        // the bridge opens its socket from a coroutine: wait for it
                        repeat(250) { if (socketCount() == before) delay(20) }
                        if (socketCount() == before) fail("the client opened no socket")
                        seen = 0
                    }
                    step.expectSent != undefined -> {
                        val message = nextSent(seen) { seen = it }
                        matcher.match(step.expectSent, message)
                    }
                    step.expectNothingSent != undefined -> {
                        delay(500)
                        while (seen < sentCount()) {
                            val message = withoutKeepAlives(JSON.parse<dynamic>(sentAt(seen)))
                            seen++
                            if (message != null) fail("expected nothing sent, got ${JSON.stringify(message)}")
                        }
                    }
                    step.receive != undefined -> {
                        val message = withServerKey(matcher.substitute(step.receive))
                        deliver(JSON.stringify(message))
                        delay(50)
                    }
                    step.setField != undefined -> bridge!!.setFormField(
                        vsid = step.setField.vsid as String,
                        fieldName = step.setField.name as String,
                        fieldValue = step.setField.value,
                    )
                    step.fire != undefined -> bridge!!.submit(step.fire.vsid as String, (step.fire.event as Number).toInt())
                    step.expectScope != undefined -> {
                        val vsid = matcher.substitute(step.expectScope.vsid) as String
                        val binding = bridge!!.viewMap[vsid] ?: fail("no view $vsid")
                        matcher.match(step.expectScope.scope, binding.values)
                    }
                    step.expectNoView != undefined -> {
                        val vsid = matcher.substitute(step.expectNoView) as String
                        if (bridge!!.viewMap[vsid] != null) fail("the view $vsid is still there")
                    }
                    step.expectOutdated != undefined -> if (bridge!!.outdated != (step.expectOutdated == true)) {
                        fail("expected outdated = ${step.expectOutdated}")
                    }
                    else -> fail("unknown step ${JSON.stringify(step)}")
                }
            } catch (e: Throwable) {
                throw AssertionError("step ${index + 1}: ${e.message ?: e.toString()}")
            }
        }
        bridge?.stop()
    }

    // :: The fake socket

    private fun socket(): dynamic = js("globalThis.__sockets[globalThis.__sockets.length - 1]")

    private fun socketCount(): Int = js("globalThis.__sockets.length") as Int

    private fun sentCount(): Int = (socket()?.sent?.length as? Int) ?: 0

    private fun sentAt(i: Int): String = socket().sent[i] as String

    private fun deliver(text: String) {
        val init: dynamic = js("({})")
        init.data = text
        val event: dynamic = js("new MessageEvent('message', init)")
        socket().onmessage(event)
    }

    /** The next message sent that is not only a keep-alive, waiting up to 5 s for it. */
    private suspend fun nextSent(from: Int, advance: (Int) -> Unit): dynamic {
        var i = from
        repeat(250) {
            while (i < sentCount()) {
                val message = withoutKeepAlives(JSON.parse<dynamic>(sentAt(i)))
                i++
                advance(i)
                if (message != null) return message
            }
            delay(20)
        }
        fail("nothing was sent")
    }

    /** The message without the browser's keep-alive events; `null` if nothing but its request id is left. */
    private fun withoutKeepAlives(message: dynamic): dynamic {
        val events = message.event
        if (events != undefined) {
            val kept = events.unsafeCast<Array<String>>().filter { it != "${RemoteBridge.BROWSER_VSID}:2" }
            if (kept.isEmpty()) js("delete message.event") else message.event = kept.toTypedArray()
        }
        val keys = js("Object.keys")(message).unsafeCast<Array<String>>()
        return if (keys.all { it == "requestId" }) null else message
    }

    /** `"$serverKey"`: a new P-256 public key, as a host makes one to answer the handshake. */
    private suspend fun withServerKey(message: dynamic): dynamic {
        if (message.serverKey == "\$serverKey") {
            val subtle: dynamic = js("globalThis.crypto.subtle")
            val pair: dynamic = subtle.generateKey(js("({ name: 'ECDH', namedCurve: 'P-256' })"), true, arrayOf("deriveBits"))
                .unsafeCast<Promise<dynamic>>().await()
            val spki = subtle.exportKey("spki", pair.publicKey).unsafeCast<Promise<ArrayBuffer>>().await()
            message.serverKey = Base64.encode(Int8Array(spki).unsafeCast<ByteArray>())
        }
        return message
    }
}
