package br.com.wdc.store.persistence.client

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bytes go and come back as they are (a product image): every one of the 256 values, sent by `putBytes` and read by
 * `getBytes`, through a `fetch` that records what it got and answers with all 256.
 */
class BinaryBodyTest {

    private val everyByte = ByteArray(256) { it.toByte() }

    @AfterTest
    fun restoreFetch() {
        js("if (globalThis.__savedFetch) { globalThis.fetch = globalThis.__savedFetch; globalThis.__savedFetch = undefined }")
    }

    /** A fetch that records the request and answers [status] with [body]. */
    private fun installFetch(status: Int, body: dynamic) {
        val answer: dynamic = js("({})")
        answer.status = status
        answer.body = body
        js("globalThis.__answer = answer")
        js(
            """
            globalThis.__savedFetch = globalThis.fetch;
            globalThis.fetch = function (url, init) {
                var b = init.body;
                globalThis.__request = {
                    method: init.method,
                    contentType: init.headers['Content-Type'],
                    body: b == null ? null : Array.from(new Uint8Array(b.buffer, b.byteOffset, b.byteLength))
                };
                return Promise.resolve(new Response(globalThis.__answer.body, { status: globalThis.__answer.status }));
            }
            """,
        )
    }

    @Test
    fun putBytes_sendsEveryByteAsItIs() = runTest {
        installFetch(200, """{"success":true}""")
        val saved = JsHttpTransport("http://x").putBytes("/api/repo/product/1/image", everyByte)

        assertTrue(saved)
        assertEquals("PUT", js("globalThis.__request.method"))
        assertEquals("application/octet-stream", js("globalThis.__request.contentType"))
        val sent = js("globalThis.__request.body").unsafeCast<Array<Int>>()
        assertContentEquals(everyByte, ByteArray(sent.size) { sent[it].toByte() })
    }

    @Test
    fun getBytes_readsEveryByteAsItCame() = runTest {
        installFetch(200, js("new Uint8Array(256).map(function (_, i) { return i })"))
        val read = JsHttpTransport("http://x").getBytes("/api/repo/product/1/image")
        assertContentEquals(everyByte, read)
    }
}
