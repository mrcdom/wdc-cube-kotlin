package br.com.wdc.store.persistence.client

import br.com.wdc.store.domain.exception.BusinessException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The [HttpTransport] of the JVM and Android: OkHttp's asynchronous call, whose callback resumes the caller. No
 * thread of the caller waits on the network (on Android, not the main one either); cancelling the call cancels the
 * request.
 */
class OkHttpTransport(baseUrl: String) : BaseHttpTransport(baseUrl) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun exchange(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Answer {
        val builder = Request.Builder().url(url)
        for ((name, value) in headers) {
            if (!name.equals("Content-Type", ignoreCase = true)) builder.header(name, value)
        }
        // the body carries the Content-Type
        builder.method(method, body?.toRequestBody(headers["Content-Type"]?.toMediaTypeOrNull()))
        val call = client.newCall(builder.build())

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(BusinessException.wrap("Network error: $method $url", e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val answer = try {
                        response.use { Answer(it.code, it.body?.bytes() ?: ByteArray(0)) }
                    } catch (e: IOException) {
                        continuation.resumeWithException(BusinessException.wrap("Network error: $method $url", e))
                        return
                    }
                    continuation.resume(answer)
                }
            })
        }
    }
}
