package br.com.wdc.shopping.persistence.client

interface HttpTransport {

    var accessTokenSupplier: (() -> String?)?

    fun postJson(path: String, body: Map<String, Any?>): Map<String, Any?>

    fun postJsonNullable(path: String, body: Map<String, Any?>): Map<String, Any?>?

    fun postJsonPublic(path: String, body: Map<String, Any?>): Map<String, Any?>

    fun postJsonWithAuth(path: String, body: Map<String, Any?>, token: String): Map<String, Any?>

    fun getJson(path: String): Map<String, Any?>

    fun getBytes(path: String): ByteArray?

    fun putBytes(path: String, data: ByteArray): Boolean
}
