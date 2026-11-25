package br.com.wdc.store.persistence.client

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@JsFun("""(status, text, everyByte) => {
    globalThis.__savedFetch = globalThis.fetch;
    globalThis.fetch = (url, init) => {
        const b = init.body;
        globalThis.__request = {
            method: init.method,
            contentType: init.headers['Content-Type'],
            body: b == null ? null : Array.from(new Uint8Array(b.buffer, b.byteOffset, b.byteLength)),
        };
        const answer = everyByte ? new Uint8Array(256).map((_, i) => i) : text;
        return Promise.resolve(new Response(answer, { status: status }));
    };
}""")
private external fun installFetch(status: Int, text: JsString, everyByte: Boolean)

@JsFun("() => { if (globalThis.__savedFetch) { globalThis.fetch = globalThis.__savedFetch; globalThis.__savedFetch = undefined; } }")
private external fun restoreFetch()

@JsFun("() => globalThis.__request.method")
private external fun requestMethod(): JsString

@JsFun("() => globalThis.__request.contentType")
private external fun requestContentType(): JsString

@JsFun("() => globalThis.__request.body.length")
private external fun requestBodySize(): Int

@JsFun("(i) => globalThis.__request.body[i]")
private external fun requestBodyAt(i: Int): Int

/**
 * Bytes go and come back as they are (a product image): every one of the 256 values, sent by `putBytes` and read by
 * `getBytes`, through a `fetch` that records what it got and answers with all 256.
 */
class BinaryBodyTest {

    private val everyByte = ByteArray(256) { it.toByte() }

    @AfterTest
    fun tearDown() = restoreFetch()

    @Test
    fun putBytes_sendsEveryByteAsItIs() = runTest {
        installFetch(200, """{"success":true}""".toJsString(), false)
        val saved = WasmHttpTransport("http://x").putBytes("/api/repo/product/1/image", everyByte)

        assertTrue(saved)
        assertEquals("PUT", requestMethod().toString())
        assertEquals("application/octet-stream", requestContentType().toString())
        assertContentEquals(everyByte, ByteArray(requestBodySize()) { requestBodyAt(it).toByte() })
    }

    @Test
    fun getBytes_readsEveryByteAsItCame() = runTest {
        installFetch(200, "".toJsString(), true)
        val read = WasmHttpTransport("http://x").getBytes("/api/repo/product/1/image")
        assertContentEquals(everyByte, read)
    }
}
