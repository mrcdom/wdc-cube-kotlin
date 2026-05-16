package br.com.wdc.shopping.persistence.client

import br.com.wdc.shopping.domain.exception.BusinessException
import org.w3c.xhr.XMLHttpRequest

/**
 * HttpTransport implementation for Kotlin/JS using synchronous XMLHttpRequest.
 */
class JsHttpTransport(private val baseUrl: String) : HttpTransport {

    override var accessTokenSupplier: (() -> String?)? = null
    override var refreshHandler: (() -> Boolean)? = null
    override var onAuthFailure: (() -> Unit)? = null

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
        val xhr = XMLHttpRequest()
        xhr.open("PUT", baseUrl + path, async = false)
        xhr.setRequestHeader("Content-Type", OCTET_CONTENT_TYPE)
        authHeader()?.let { xhr.setRequestHeader("Authorization", it) }

        // Convert ByteArray to Int8Array for sending
        val jsArray = js("new Int8Array(data)").unsafeCast<org.khronos.webgl.Int8Array>()
        xhr.send(jsArray)

        val status = xhr.status.toInt()
        if (status in 200..299) {
            val text = xhr.responseText
            return text.contains("\"success\":true") || text.contains("\"success\": true")
        }
        throw BusinessException("HTTP $status")
    }

    private fun authHeader(): String? {
        val supplier = accessTokenSupplier ?: return null
        val token = supplier() ?: return null
        return "Bearer $token"
    }

    private fun doRequest(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        authorization: String?
    ): String? {
        val xhr = XMLHttpRequest()
        xhr.open(method, url, async = false)
        contentType?.let { xhr.setRequestHeader("Content-Type", it) }
        authorization?.let { xhr.setRequestHeader("Authorization", it) }

        if (body != null) xhr.send(body) else xhr.send()

        val status = xhr.status.toInt()
        val responseText = xhr.responseText

        if (status in 200..299) {
            return responseText
        }

        if (status == 401 && authorization != null) {
            if (refreshHandler?.invoke() == true) {
                return doRequest(method, url, body, contentType, authHeader())
            }
            onAuthFailure?.invoke()
        }

        throw BusinessException("HTTP $status: $responseText")
    }

    private fun doRequestNullable(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        authorization: String?
    ): String? {
        val xhr = XMLHttpRequest()
        xhr.open(method, url, async = false)
        contentType?.let { xhr.setRequestHeader("Content-Type", it) }
        authorization?.let { xhr.setRequestHeader("Authorization", it) }

        if (body != null) xhr.send(body) else xhr.send()

        val status = xhr.status.toInt()
        if (status == 404) return null
        val responseText = xhr.responseText
        if (status in 200..299) {
            return responseText
        }

        if (status == 401 && authorization != null) {
            if (refreshHandler?.invoke() == true) {
                return doRequestNullable(method, url, body, contentType, authHeader())
            }
            onAuthFailure?.invoke()
        }

        throw BusinessException("HTTP $status: $responseText")
    }

    private fun doRequestBytes(
        method: String,
        url: String,
        authorization: String?
    ): ByteArray? {
        val xhr = XMLHttpRequest()
        xhr.open(method, url, async = false)
        xhr.asDynamic().responseType = "arraybuffer"
        authorization?.let { xhr.setRequestHeader("Authorization", it) }

        xhr.send()

        val status = xhr.status.toInt()
        if (status == 404 || status == 204) return null
        if (status in 200..299) {
            val buffer = xhr.asDynamic().response ?: return null
            val uint8Array = js("new Uint8Array(buffer)")
            val len: Int = uint8Array.length as Int
            val result = ByteArray(len)
            for (i in 0 until len) {
                result[i] = (uint8Array[i] as Int).toByte()
            }
            return result
        }

        if (status == 401 && authorization != null) {
            if (refreshHandler?.invoke() == true) {
                return doRequestBytes(method, url, authHeader())
            }
            onAuthFailure?.invoke()
        }

        throw BusinessException("HTTP $status")
    }

    companion object {
        private const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
        private const val OCTET_CONTENT_TYPE = "application/octet-stream"
    }
}
