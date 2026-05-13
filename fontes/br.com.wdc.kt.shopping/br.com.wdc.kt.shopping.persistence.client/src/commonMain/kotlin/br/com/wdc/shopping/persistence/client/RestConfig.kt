package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory

class RestConfig(val transport: HttpTransport) {

    var authClient: RestAuthClient? = null
        internal set

    fun setAuthClientInstance(authClient: RestAuthClient?) {
        this.authClient = authClient
        transport.accessTokenSupplier = if (authClient != null) {
            { authClient.accessToken }
        } else {
            null
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

    fun postJson(path: String, body: String): ExtensibleObjectInput =
        fromJson(transport.postJson(path, body))

    fun postJsonNullable(path: String, body: String): ExtensibleObjectInput? {
        val json = transport.postJsonNullable(path, body) ?: return null
        return fromJson(json)
    }

    fun postJsonPublic(path: String, body: String): ExtensibleObjectInput =
        fromJson(transport.postJsonPublic(path, body))

    fun postJsonWithAuth(path: String, body: String, token: String): ExtensibleObjectInput =
        fromJson(transport.postJsonWithAuth(path, body, token))

    fun getJson(path: String): ExtensibleObjectInput =
        fromJson(transport.getJson(path))

    fun getBytes(path: String): ByteArray? =
        transport.getBytes(path)

    fun putBytes(path: String, data: ByteArray): Boolean =
        transport.putBytes(path, data)
}
