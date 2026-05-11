package br.com.wdc.shopping.persistence.client

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

    fun postJson(path: String, body: Map<String, Any?>) =
        transport.postJson(path, body)

    fun postJsonNullable(path: String, body: Map<String, Any?>) =
        transport.postJsonNullable(path, body)

    fun postJsonPublic(path: String, body: Map<String, Any?>) =
        transport.postJsonPublic(path, body)

    fun postJsonWithAuth(path: String, body: Map<String, Any?>, token: String) =
        transport.postJsonWithAuth(path, body, token)

    fun getJson(path: String) =
        transport.getJson(path)

    fun getBytes(path: String) =
        transport.getBytes(path)

    fun putBytes(path: String, data: ByteArray) =
        transport.putBytes(path, data)
}
