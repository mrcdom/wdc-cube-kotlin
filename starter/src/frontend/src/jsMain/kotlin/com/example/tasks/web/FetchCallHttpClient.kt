package com.example.tasks.web

import br.com.wdc.framework.domain.exception.BusinessException
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.exception.NotFoundException
import com.example.tasks.data.CallHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * The calls' HTTP in the browser: `fetch` to the backend that served the page, awaited without blocking it. A refusal
 * comes back as the exception the backend threw (400 [InvalidRequestException], 404 [NotFoundException]), anything else
 * as [BusinessException]. Cancelling the call aborts the request.
 */
class FetchCallHttpClient : CallHttpClient {

    override suspend fun post(path: String, json: String): String {
        val init: dynamic = js("({})")
        init.method = "POST"
        init.headers = js("({ 'Content-Type': 'application/json' })")
        init.body = json
        val controller: dynamic = js("new AbortController()")
        init.signal = controller.signal

        try {
            val response: dynamic = try {
                fetch(path, init).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // fetch rejects only when the backend could not be reached; any status is an answer
                throw BusinessException("Network error: POST $path: ${e.message}")
            }
            val text = response.text().unsafeCast<Promise<String>>().await()
            return when (val status = response.status.unsafeCast<Int>()) {
                in 200..299 -> text
                400 -> throw InvalidRequestException(text)
                404 -> throw NotFoundException(text)
                else -> throw BusinessException("HTTP $status: $text")
            }
        } catch (e: CancellationException) {
            controller.abort()
            throw e
        }
    }

    private fun fetch(url: String, init: dynamic): Promise<dynamic> =
        js("globalThis.fetch")(url, init).unsafeCast<Promise<dynamic>>()
}
