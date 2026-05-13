package br.com.wdc.shopping.persistence.client

interface HttpTransport {

    var accessTokenSupplier: (() -> String?)?

    fun postJson(path: String, body: String): String

    fun postJsonNullable(path: String, body: String): String?

    fun postJsonPublic(path: String, body: String): String

    fun postJsonWithAuth(path: String, body: String, token: String): String

    fun getJson(path: String): String

    fun getBytes(path: String): ByteArray?

    fun putBytes(path: String, data: ByteArray): Boolean
}
