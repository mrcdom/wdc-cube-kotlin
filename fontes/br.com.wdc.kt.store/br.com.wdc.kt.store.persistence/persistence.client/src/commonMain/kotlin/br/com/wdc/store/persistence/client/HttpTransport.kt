package br.com.wdc.store.persistence.client

/**
 * The HTTP calls of the client to the backend. Every call suspends while it waits for the network, on every platform:
 * no thread is blocked. [BaseHttpTransport] does what is common (the session's token, its renewal on 401, the remote
 * transaction's header); each platform only sends a request ([BaseHttpTransport.exchange]).
 */
interface HttpTransport {

    var accessTokenSupplier: (() -> String?)?

    /**
     * Renews the session after the server refused [staleToken] (401); `true` if a call can be repeated with the
     * current token. Several calls refused at once all come here: only one renews, the others find the token
     * already replaced ([RestConfig]).
     */
    var refreshHandler: (suspend (staleToken: String) -> Boolean)?

    /** Called when authentication fails irrecoverably (refresh also failed). */
    var onAuthFailure: (() -> Unit)?

    /**
     * Identifier of this client instance, sent on **all** calls (`X-Client-Id`). It owns the remote
     * transactions when there is no authenticated user.
     */
    val clientId: String

    /**
     * Inside a remote transaction of this transport ([RestTransactionService], or [inRemoteTransaction]), the
     * authenticated calls carry its id in `X-Tx-Id` — including `putBytes` —, and writes join that transaction on the
     * server. The transaction goes with the coroutine that opened it, not with the transport: another action running
     * meanwhile does not inherit it.
     */
    suspend fun postJson(path: String, body: String): String

    suspend fun postJsonNullable(path: String, body: String): String?

    /** A POST without the session: no token, no remote transaction, no renewal (login, refresh). */
    suspend fun postJsonPublic(path: String, body: String): String

    suspend fun postJsonWithAuth(path: String, body: String, token: String): String

    /**
     * A GET without the session: no token, no remote transaction, no renewal (the login challenge). The authenticated
     * reads go by POST ([postJson]), so none needs an authenticated GET.
     */
    suspend fun getJsonPublic(path: String): String

    /** The bytes at [path]; `null` for 204 (no content). Any other failure, 404 included, throws ([httpFailure]). */
    suspend fun getBytes(path: String): ByteArray?

    suspend fun putBytes(path: String, data: ByteArray): Boolean
}
