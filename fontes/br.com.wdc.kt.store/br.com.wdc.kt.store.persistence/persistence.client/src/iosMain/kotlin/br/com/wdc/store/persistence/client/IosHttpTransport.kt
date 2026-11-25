package br.com.wdc.store.persistence.client

import br.com.wdc.store.domain.exception.BusinessException
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The [HttpTransport] of iOS: an `NSURLSession` data task, whose completion resumes the call. No thread waits for
 * it (the main thread's run loop is not spun, as it was with the old blocking transport); cancelling the call
 * cancels the task.
 */
class IosHttpTransport(baseUrl: String) : BaseHttpTransport(baseUrl) {

    override suspend fun exchange(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Answer {
        val nsUrl = NSURL.URLWithString(url) ?: throw BusinessException("Invalid URL: $url")
        val request = NSMutableURLRequest.requestWithURL(nsUrl)
        request.setHTTPMethod(method)
        for ((name, value) in headers) request.setValue(value, forHTTPHeaderField = name)
        if (body != null) request.setHTTPBody(body.toNSData())

        return suspendCancellableCoroutine { continuation ->
            val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
                if (error != null) {
                    continuation.resumeWithException(BusinessException("Network error: $method $url: ${error.localizedDescription}"))
                } else {
                    val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
                    continuation.resume(Answer(status, data?.toByteArray() ?: ByteArray(0)))
                }
            }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }
}

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}

private fun NSData.toByteArray(): ByteArray {
    val len = this.length.toInt()
    if (len == 0) return ByteArray(0)
    val bytes = ByteArray(len)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}
