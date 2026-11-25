package br.com.wdc.store.persistence.client

import br.com.wdc.store.domain.exception.BusinessException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlin.js.Promise

/** The [HttpTransport] of Kotlin/Wasm: the browser's `fetch`, awaited without blocking the page; cancelling the call aborts it. */
class WasmHttpTransport(baseUrl: String) : BaseHttpTransport(baseUrl) {

    override suspend fun exchange(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Answer {
        val headerObject = jsNewObject()
        for ((name, value) in headers) jsSetString(headerObject, name.toJsString(), value.toJsString())

        // cancelling the call aborts the request, the body's reading included
        val controller = jsNewAbortController()
        val result = try {
            jsFetch(method.toJsString(), url.toJsString(), headerObject, body?.let(::byteArrayToJsUint8Array), jsSignalOf(controller))
                .await<JsAny>()
        } catch (e: CancellationException) {
            jsAbort(controller)
            throw e
        } catch (e: Throwable) {
            // fetch rejects only when the server could not be reached; any status is an answer
            throw BusinessException("Network error: $method $url: ${e.message}")
        }
        return Answer(jsResultStatus(result), jsUint8ArrayToByteArray(jsResultData(result)))
    }
}

// --- JS Interop ---

@JsFun("""(method, url, headers, body, signal) =>
    fetch(url, { method: method, headers: headers, body: body === null ? undefined : body, signal: signal })
        .then(response => response.arrayBuffer().then(buffer => ({ status: response.status, data: new Uint8Array(buffer) })))""")
private external fun jsFetch(method: JsString, url: JsString, headers: JsAny, body: JsAny?, signal: JsAny): Promise<JsAny>

@JsFun("() => new AbortController()")
private external fun jsNewAbortController(): JsAny

@JsFun("(c) => c.signal")
private external fun jsSignalOf(c: JsAny): JsAny

@JsFun("(c) => c.abort()")
private external fun jsAbort(c: JsAny)

@JsFun("() => ({})")
private external fun jsNewObject(): JsAny

@JsFun("(o, k, v) => { o[k] = v; }")
private external fun jsSetString(o: JsAny, k: JsString, v: JsString)

@JsFun("(r) => r.status")
private external fun jsResultStatus(r: JsAny): Int

@JsFun("(r) => r.data")
private external fun jsResultData(r: JsAny): JsAny

private fun jsUint8ArrayToByteArray(uint8Array: JsAny): ByteArray {
    val len = jsUint8ArrayLength(uint8Array)
    val result = ByteArray(len)
    for (i in 0 until len) {
        result[i] = jsUint8ArrayGet(uint8Array, i).toByte()
    }
    return result
}

@JsFun("(a) => a.length")
private external fun jsUint8ArrayLength(a: JsAny): Int

@JsFun("(a, i) => a[i]")
private external fun jsUint8ArrayGet(a: JsAny, i: Int): Int

private fun byteArrayToJsUint8Array(bytes: ByteArray): JsAny {
    val array = jsUint8ArrayCreate(bytes.size)
    for (i in bytes.indices) {
        jsUint8ArraySet(array, i, bytes[i].toInt() and 0xFF)
    }
    return array
}

@JsFun("(n) => new Uint8Array(n)")
private external fun jsUint8ArrayCreate(n: Int): JsAny

@JsFun("(a, i, v) => { a[i] = v; }")
private external fun jsUint8ArraySet(a: JsAny, i: Int, v: Int)
