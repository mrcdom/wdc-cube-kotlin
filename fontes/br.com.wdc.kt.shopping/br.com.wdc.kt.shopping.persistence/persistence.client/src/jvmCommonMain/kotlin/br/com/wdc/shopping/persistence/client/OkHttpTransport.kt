package br.com.wdc.shopping.persistence.client

import br.com.wdc.shopping.domain.exception.BusinessException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpTransport(private val baseUrl: String) : HttpTransport {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val octetMediaType = "application/octet-stream".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override var accessTokenSupplier: (() -> String?)? = null
    override var refreshHandler: (() -> Boolean)? = null
    override var onAuthFailure: (() -> Unit)? = null
    override var transactionIdSupplier: (() -> String?)? = null
    override val clientId: String = newClientId()

    override fun postJson(path: String, body: String): String =
        authenticated("POST $path", { Request.Builder().url(baseUrl + path).post(body.toRequestBody(jsonMediaType)) }) { response ->
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                throw httpFailure(response.code, responseBody)
            }
            responseBody ?: ""
        }

    override fun postJsonNullable(path: String, body: String): String? =
        authenticated("POST $path", { Request.Builder().url(baseUrl + path).post(body.toRequestBody(jsonMediaType)) }) { response ->
            if (response.code == 404) return@authenticated null
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                throw httpFailure(response.code, responseBody)
            }
            responseBody ?: ""
        }

    override fun postJsonPublic(path: String, body: String): String {
        val request = Request.Builder()
            .url(baseUrl + path)
            .post(body.toRequestBody(jsonMediaType))
            .header(CLIENT_HEADER, clientId)
            .build()
        return executeForString(request, "POST $path")
    }

    override fun postJsonWithAuth(path: String, body: String, token: String): String {
        val request = Request.Builder()
            .url(baseUrl + path)
            .post(body.toRequestBody(jsonMediaType))
            .header("Authorization", "Bearer $token")
            .header(CLIENT_HEADER, clientId)
            .build()
        return executeForString(request, "POST $path")
    }

    override fun getJson(path: String): String {
        val request = Request.Builder()
            .url(baseUrl + path)
            .get()
            .header(CLIENT_HEADER, clientId)
            .build()
        return executeForString(request, "GET $path")
    }

    override fun getBytes(path: String): ByteArray? =
        authenticated("GET $path", { Request.Builder().url(baseUrl + path).get() }) { response ->
            if (response.code == 404 || response.code == 204) return@authenticated null
            if (!response.isSuccessful) {
                throw httpFailure(response.code)
            }
            response.body?.bytes()
        }

    override fun putBytes(path: String, data: ByteArray): Boolean =
        authenticated("PUT $path", { Request.Builder().url(baseUrl + path).put(data.toRequestBody(octetMediaType)) }) { response ->
            if (!response.isSuccessful) {
                throw httpFailure(response.code)
            }
            val responseBody = response.body?.string() ?: return@authenticated false
            responseBody.contains("\"success\":true") || responseBody.contains("\"success\": true")
        }

    /**
     * Executa uma chamada autenticada. Se o servidor recusar o token (401), renova a sessão pelo
     * [refreshHandler] e repete a chamada **uma vez**, já com o token novo; se não houver como renovar, avisa
     * o [onAuthFailure] e a recusa sobe para o chamador.
     *
     * @param request monta a requisição sem os cabeçalhos de autenticação — é chamada de novo na repetição
     */
    private fun <T> authenticated(label: String, request: () -> Request.Builder, read: (Response) -> T): T {
        try {
            val first = request()
            val sentToken = addAuthHeader(first)
            val refusal = client.newCall(first.build()).execute().use { response ->
                if (response.code != 401 || !sentToken) {
                    return read(response)
                }
                response.body?.string()
            }
            if (refreshHandler?.invoke() != true) {
                onAuthFailure?.invoke()
                throw httpFailure(401, refusal)
            }
            val retry = request()
            addAuthHeader(retry)
            client.newCall(retry.build()).execute().use { response ->
                return read(response)
            }
        } catch (e: IOException) {
            throw BusinessException.wrap(label, e)
        }
    }

    private fun executeForString(request: Request, label: String): String {
        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw httpFailure(response.code, responseBody)
                }
                return responseBody ?: ""
            }
        } catch (e: BusinessException) {
            throw e
        } catch (e: IOException) {
            throw BusinessException.wrap(label, e)
        }
    }

    /**
     * Os cabeçalhos de uma chamada autenticada: o token, o cliente e a transação remota corrente.
     *
     * @return se havia token para enviar
     */
    private fun addAuthHeader(builder: Request.Builder): Boolean {
        builder.header(CLIENT_HEADER, clientId)
        currentTxId()?.let { builder.header(TX_HEADER, it) }
        val token = accessTokenSupplier?.invoke() ?: return false
        builder.header("Authorization", "Bearer $token")
        return true
    }
}
