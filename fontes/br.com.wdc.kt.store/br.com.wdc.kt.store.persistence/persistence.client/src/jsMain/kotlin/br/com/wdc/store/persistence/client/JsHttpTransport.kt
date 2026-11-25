package br.com.wdc.store.persistence.client

import br.com.wdc.store.domain.exception.BusinessException
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.js.Promise

/** The [HttpTransport] of Kotlin/JS: the browser's `fetch`, awaited without blocking the page. */
class JsHttpTransport(baseUrl: String) : BaseHttpTransport(baseUrl) {

    override suspend fun exchange(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Answer {
        val init: dynamic = js("({})")
        init.method = method
        val headerObject: dynamic = js("({})")
        for ((name, value) in headers) headerObject[name] = value
        init.headers = headerObject
        // a ByteArray is an Int8Array in Kotlin/JS
        if (body != null) init.body = body.unsafeCast<Int8Array>()

        val response: dynamic = try {
            fetch(url, init).await()
        } catch (e: Throwable) {
            // fetch rejects only when the server could not be reached; any status is an answer
            throw BusinessException("Network error: $method $url: ${e.message}")
        }
        val buffer = response.arrayBuffer().unsafeCast<Promise<ArrayBuffer>>().await()
        return Answer(response.status.unsafeCast<Int>(), Int8Array(buffer).unsafeCast<ByteArray>())
    }

    private fun fetch(url: String, init: dynamic): Promise<dynamic> =
        js("globalThis.fetch")(url, init).unsafeCast<Promise<dynamic>>()
}
