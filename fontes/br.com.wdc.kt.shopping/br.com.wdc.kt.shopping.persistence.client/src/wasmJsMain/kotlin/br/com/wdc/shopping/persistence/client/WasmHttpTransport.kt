package br.com.wdc.shopping.persistence.client

import br.com.wdc.shopping.domain.exception.BusinessException

/**
 * HttpTransport implementation for wasmJs using synchronous XMLHttpRequest.
 * Note: synchronous XHR is deprecated in browsers but necessary for the current
 * synchronous HttpTransport interface. A future refactoring to async/coroutines
 * would be preferable.
 */
class WasmHttpTransport(private val baseUrl: String) : HttpTransport {

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
        return doRequestBytes("GET", baseUrl + path, null, null, authHeader())
    }

    override fun putBytes(path: String, data: ByteArray): Boolean {
        val response = doRequest("PUT", baseUrl + path, null, OCTET_CONTENT_TYPE, authHeader(),
            sendBytes = data)
        return response?.contains("\"success\":true") == true ||
               response?.contains("\"success\": true") == true
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
        authorization: String?,
        sendBytes: ByteArray? = null
    ): String? {
        val result = xhrSyncRequest(
            method.toJsString(),
            url.toJsString(),
            body?.toJsString() ?: "".toJsString(),
            (contentType ?: "").toJsString(),
            (authorization ?: "").toJsString()
        )
        val status = xhrResultStatus(result)
        val responseText = xhrResultBody(result).toString()

        if (status in 200..299) {
            return responseText
        }

        // On 401 with auth header: try refresh and retry once
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
        val result = xhrSyncRequest(
            method.toJsString(),
            url.toJsString(),
            body?.toJsString() ?: "".toJsString(),
            (contentType ?: "").toJsString(),
            (authorization ?: "").toJsString()
        )
        val status = xhrResultStatus(result)
        if (status == 404) return null
        val responseText = xhrResultBody(result).toString()
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
        body: String?,
        contentType: String?,
        authorization: String?
    ): ByteArray? {
        val result = xhrSyncRequestBytes(
            method.toJsString(),
            url.toJsString(),
            body?.toJsString() ?: "".toJsString(),
            (contentType ?: "").toJsString(),
            (authorization ?: "").toJsString()
        )
        val status = xhrBytesResultStatus(result)
        if (status == 404 || status == 204) return null
        if (status in 200..299) {
            val jsArray = xhrBytesResultData(result) ?: return null
            return jsUint8ArrayToByteArray(jsArray)
        }

        if (status == 401 && authorization != null) {
            if (refreshHandler?.invoke() == true) {
                return doRequestBytes(method, url, body, contentType, authHeader())
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

// --- JS Interop ---

@JsFun("""(method, url, body, contentType, authorization) => {
    const xhr = new XMLHttpRequest();
    xhr.open(method, url, false);
    if (contentType !== '') xhr.setRequestHeader('Content-Type', contentType);
    if (authorization !== '') xhr.setRequestHeader('Authorization', authorization);
    xhr.send(body !== '' ? body : null);
    return { status: xhr.status, body: xhr.responseText };
}""")
private external fun xhrSyncRequest(
    method: JsString, url: JsString, body: JsString,
    contentType: JsString, authorization: JsString
): JsAny

@JsFun("(r) => r.status")
private external fun xhrResultStatus(r: JsAny): Int

@JsFun("(r) => r.body")
private external fun xhrResultBody(r: JsAny): JsString

@JsFun("""(method, url, body, contentType, authorization) => {
    const xhr = new XMLHttpRequest();
    xhr.open(method, url, false);
    xhr.responseType = 'arraybuffer';
    if (contentType !== '') xhr.setRequestHeader('Content-Type', contentType);
    if (authorization !== '') xhr.setRequestHeader('Authorization', authorization);
    xhr.send(body !== '' ? body : null);
    return { status: xhr.status, data: xhr.response ? new Uint8Array(xhr.response) : null };
}""")
private external fun xhrSyncRequestBytes(
    method: JsString, url: JsString, body: JsString,
    contentType: JsString, authorization: JsString
): JsAny

@JsFun("(r) => r.status")
private external fun xhrBytesResultStatus(r: JsAny): Int

@JsFun("(r) => r.data")
private external fun xhrBytesResultData(r: JsAny): JsAny?

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
