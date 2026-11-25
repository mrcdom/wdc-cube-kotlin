package br.com.wdc.store.persistence.client

/**
 * What every [HttpTransport] does the same way, whatever the platform: the headers (client, session token, remote
 * transaction), the renewal of the session when the server refuses its token, and reading each kind of answer. A
 * platform implements only [exchange]: send one request and wait for its answer without blocking a thread.
 */
abstract class BaseHttpTransport(private val baseUrl: String) : HttpTransport {

    override var accessTokenSupplier: (() -> String?)? = null
    override var refreshHandler: (suspend (staleToken: String) -> Boolean)? = null
    override var onAuthFailure: (() -> Unit)? = null
    override val clientId: String = newClientId()

    /** An answer of the server: the status and the body as it came. */
    class Answer(val status: Int, val body: ByteArray) {
        val ok: Boolean get() = status in 200..299
        fun text(): String = body.decodeToString()
    }

    /**
     * Sends one request and suspends until the server answers, whatever the status. A failure to reach the server
     * (connection refused, timeout) throws, with a message that does not start with "HTTP " ([isHttpAnswer]).
     *
     * @param headers every header to send, `Content-Type` included when there is a [body]
     */
    protected abstract suspend fun exchange(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Answer

    override suspend fun postJson(path: String, body: String): String {
        val answer = session("POST", path, body.encodeToByteArray(), JSON_CONTENT_TYPE)
        return answer.textOrThrow()
    }

    override suspend fun postJsonNullable(path: String, body: String): String? {
        val answer = session("POST", path, body.encodeToByteArray(), JSON_CONTENT_TYPE)
        if (answer.status == 404) return null
        return answer.textOrThrow()
    }

    override suspend fun postJsonPublic(path: String, body: String): String =
        send("POST", path, body.encodeToByteArray(), JSON_CONTENT_TYPE, authorization = null, txId = null).textOrThrow()

    override suspend fun postJsonWithAuth(path: String, body: String, token: String): String =
        send("POST", path, body.encodeToByteArray(), JSON_CONTENT_TYPE, authorization = "Bearer $token", txId = null).textOrThrow()

    override suspend fun getJsonPublic(path: String): String =
        send("GET", path, null, null, authorization = null, txId = null).textOrThrow()

    override suspend fun getBytes(path: String): ByteArray? {
        val answer = session("GET", path, null, null)
        // 204 = no content ("no image"); 404 = not found, an error like the others
        if (answer.status == 204) return null
        if (!answer.ok) throw httpFailure(answer.status)
        return answer.body
    }

    override suspend fun putBytes(path: String, data: ByteArray): Boolean {
        val answer = session("PUT", path, data, OCTET_CONTENT_TYPE)
        if (!answer.ok) throw httpFailure(answer.status)
        val text = answer.text()
        return text.contains("\"success\":true") || text.contains("\"success\": true")
    }

    /**
     * A call with the session: its token and the current remote transaction. If the server refuses the token (401),
     * the session is renewed through [refreshHandler] and the call repeated **once**, with the new token; if it cannot
     * be renewed, [onAuthFailure] is told and the refusal goes back to the caller.
     */
    private suspend fun session(method: String, path: String, body: ByteArray?, contentType: String?): Answer {
        val txId = currentTxId()
        val token = accessTokenSupplier?.invoke()
        val first = send(method, path, body, contentType, token?.let { "Bearer $it" }, txId)
        if (first.status != 401 || token == null) return first

        if (refreshHandler?.invoke(token) != true) {
            onAuthFailure?.invoke()
            return first
        }
        val renewed = accessTokenSupplier?.invoke()
        return send(method, path, body, contentType, renewed?.let { "Bearer $it" }, txId)
    }

    private suspend fun send(
        method: String,
        path: String,
        body: ByteArray?,
        contentType: String?,
        authorization: String?,
        txId: String?,
    ): Answer {
        val headers = LinkedHashMap<String, String>()
        headers[CLIENT_HEADER] = clientId
        if (contentType != null && body != null) headers["Content-Type"] = contentType
        if (authorization != null) headers["Authorization"] = authorization
        if (txId != null) headers[TX_HEADER] = txId
        return exchange(method, baseUrl + path, headers, body)
    }

    private fun Answer.textOrThrow(): String {
        val text = text()
        if (!ok) throw httpFailure(status, text)
        return text
    }

    companion object {
        const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
        const val OCTET_CONTENT_TYPE = "application/octet-stream"
    }
}
