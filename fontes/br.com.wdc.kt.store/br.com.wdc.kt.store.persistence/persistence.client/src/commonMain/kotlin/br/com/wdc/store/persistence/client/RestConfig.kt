package br.com.wdc.store.persistence.client

import br.com.wdc.framework.domain.exception.UnauthenticatedException
import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RestConfig(val transport: HttpTransport) {

    var authClient: RestAuthClient? = null
        internal set

    /** One renewal at a time: the server replaces the refresh token on each one, so a second would be refused. */
    private val refreshLock = Mutex()

    fun setAuthClientInstance(authClient: RestAuthClient?) {
        this.authClient = authClient
        if (authClient != null) {
            transport.accessTokenSupplier = { authClient.accessToken }
            // a refused renewal loses the session; a failure to reach the server goes to the caller, the session kept.
            // Calls refused at the same time all ask: the first renews, the others find the token already replaced.
            transport.refreshHandler = { staleToken ->
                refreshLock.withLock {
                    val current = authClient.accessToken
                    when {
                        current == null -> false
                        current != staleToken -> true
                        else -> try {
                            authClient.refresh()
                            true
                        } catch (_: UnauthenticatedException) {
                            false
                        }
                    }
                }
            }
            // told once per lost session, however many calls were refused with it
            transport.onAuthFailure = { if (authClient.accessToken != null) authClient.sessionLost() }
        } else {
            transport.accessTokenSupplier = null
            transport.refreshHandler = null
            transport.onAuthFailure = null
        }
    }

    /** Create a JSON writer; call [block] to write, then the resulting JSON string is returned. */
    inline fun toJson(block: (ExtensibleObjectOutput) -> Unit): String {
        val jso = JsonOutputFactory.createStringOutput()
        block(jso.output)
        return jso.resultString()
    }

    /** Parse a JSON string into an [ExtensibleObjectInput]. */
    fun fromJson(json: String): ExtensibleObjectInput =
        JsonInputFactory.createStringInput(json).input

    suspend fun postJson(path: String, body: String): ExtensibleObjectInput =
        fromJson(transport.postJson(path, body))

    suspend fun postJsonNullable(path: String, body: String): ExtensibleObjectInput? {
        val json = transport.postJsonNullable(path, body) ?: return null
        return fromJson(json)
    }

    suspend fun postJsonPublic(path: String, body: String): ExtensibleObjectInput =
        fromJson(transport.postJsonPublic(path, body))

    suspend fun postJsonWithAuth(path: String, body: String, token: String): ExtensibleObjectInput =
        fromJson(transport.postJsonWithAuth(path, body, token))

    suspend fun getJsonPublic(path: String): ExtensibleObjectInput =
        fromJson(transport.getJsonPublic(path))

    suspend fun getBytes(path: String): ByteArray? =
        transport.getBytes(path)

    suspend fun putBytes(path: String, data: ByteArray): Boolean =
        transport.putBytes(path, data)
}
