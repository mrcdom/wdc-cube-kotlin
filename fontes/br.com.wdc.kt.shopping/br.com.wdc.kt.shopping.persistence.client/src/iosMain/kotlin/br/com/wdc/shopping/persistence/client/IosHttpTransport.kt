package br.com.wdc.shopping.persistence.client

import br.com.wdc.shopping.domain.exception.BusinessException
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.posix.memcpy

/**
 * HttpTransport implementation for iOS using synchronous NSURLSession requests.
 */
class IosHttpTransport(private val baseUrl: String) : HttpTransport {

    override var accessTokenSupplier: (() -> String?)? = null

    override fun postJson(path: String, body: String): String {
        return doRequest("POST", baseUrl + path, body, JSON_CONTENT_TYPE, authHeader())
            ?: throw BusinessException("Empty response for POST $path")
    }

    override fun postJsonNullable(path: String, body: String): String? {
        return doRequestNullable("POST", baseUrl + path, body, JSON_CONTENT_TYPE, authHeader())
    }

    override fun postJsonPublic(path: String, body: String): String {
        return doRequest("POST", baseUrl + path, body, JSON_CONTENT_TYPE, null)
            ?: throw BusinessException("Empty response for POST $path")
    }

    override fun postJsonWithAuth(path: String, body: String, token: String): String {
        return doRequest("POST", baseUrl + path, body, JSON_CONTENT_TYPE, "Bearer $token")
            ?: throw BusinessException("Empty response for POST $path")
    }

    override fun getJson(path: String): String {
        return doRequest("GET", baseUrl + path, null, null, null)
            ?: throw BusinessException("Empty response for GET $path")
    }

    override fun getBytes(path: String): ByteArray? {
        return doRequestBytes("GET", baseUrl + path, authHeader())
    }

    override fun putBytes(path: String, data: ByteArray): Boolean {
        val request = createRequest("PUT", baseUrl + path)
        request.setValue(OCTET_CONTENT_TYPE, forHTTPHeaderField = "Content-Type")
        authHeader()?.let { request.setValue(it, forHTTPHeaderField = "Authorization") }

        data.usePinned { pinned ->
            request.setHTTPBody(NSData.create(bytes = pinned.addressOf(0), length = data.size.toULong()))
        }

        val (responseData, response) = sendSynchronous(request)
        val statusCode = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
        if (statusCode in 200..299) {
            val text = responseData?.toKotlinString() ?: ""
            return text.contains("\"success\":true") || text.contains("\"success\": true")
        }
        throw BusinessException("HTTP $statusCode")
    }

    private fun authHeader(): String? {
        val supplier = accessTokenSupplier ?: return null
        val token = supplier() ?: return null
        return "Bearer $token"
    }

    private fun createRequest(method: String, url: String): NSMutableURLRequest {
        val nsUrl = NSURL.URLWithString(url) ?: throw BusinessException("Invalid URL: $url")
        val request = NSMutableURLRequest.requestWithURL(nsUrl)
        request.setHTTPMethod(method)
        return request
    }

    private fun doRequest(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        authorization: String?
    ): String? {
        val request = createRequest(method, url)
        contentType?.let { request.setValue(it, forHTTPHeaderField = "Content-Type") }
        authorization?.let { request.setValue(it, forHTTPHeaderField = "Authorization") }
        body?.let {
            request.setHTTPBody((it as NSString).dataUsingEncoding(NSUTF8StringEncoding))
        }

        val (responseData, response) = sendSynchronous(request)
        val statusCode = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
        val responseText = responseData?.toKotlinString() ?: ""

        if (statusCode in 200..299) {
            return responseText
        }
        throw BusinessException("HTTP $statusCode: $responseText")
    }

    private fun doRequestNullable(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        authorization: String?
    ): String? {
        val request = createRequest(method, url)
        contentType?.let { request.setValue(it, forHTTPHeaderField = "Content-Type") }
        authorization?.let { request.setValue(it, forHTTPHeaderField = "Authorization") }
        body?.let {
            request.setHTTPBody((it as NSString).dataUsingEncoding(NSUTF8StringEncoding))
        }

        val (responseData, response) = sendSynchronous(request)
        val statusCode = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
        if (statusCode == 404) return null
        val responseText = responseData?.toKotlinString() ?: ""
        if (statusCode in 200..299) {
            return responseText
        }
        throw BusinessException("HTTP $statusCode: $responseText")
    }

    private fun doRequestBytes(method: String, url: String, authorization: String?): ByteArray? {
        val request = createRequest(method, url)
        authorization?.let { request.setValue(it, forHTTPHeaderField = "Authorization") }

        val (responseData, response) = sendSynchronous(request)
        val statusCode = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
        if (statusCode == 404 || statusCode == 204) return null
        if (statusCode in 200..299) {
            return responseData?.toKotlinByteArray()
        }
        throw BusinessException("HTTP $statusCode")
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun sendSynchronous(request: NSMutableURLRequest): Pair<NSData?, NSURLResponse?> {
        var resultData: NSData? = null
        var resultResponse: NSURLResponse? = null
        var resultError: NSError? = null
        var done = false

        val session = NSURLSession.sharedSession
        val task = session.dataTaskWithRequest(request) { data, response, error ->
            resultData = data
            resultResponse = response
            resultError = error
            done = true
        }
        task.resume()

        // Block until complete (main thread friendly via runloop)
        val runLoop = NSRunLoop.currentRunLoop
        while (!done) {
            runLoop.runMode(NSDefaultRunLoopMode, beforeDate = NSDate.dateWithTimeIntervalSinceNow(0.01))
        }

        if (resultError != null) {
            throw BusinessException("Network error: ${resultError!!.localizedDescription}")
        }
        return Pair(resultData, resultResponse)
    }

    companion object {
        private const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
        private const val OCTET_CONTENT_TYPE = "application/octet-stream"
    }
}

private fun NSData.toKotlinString(): String {
    return NSString.create(this, NSUTF8StringEncoding) as? String ?: ""
}

private fun NSData.toKotlinByteArray(): ByteArray {
    val len = this.length.toInt()
    if (len == 0) return ByteArray(0)
    val bytes = ByteArray(len)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}
