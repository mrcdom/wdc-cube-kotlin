package br.com.wdc.shopping.persistence.client

import br.com.wdc.shopping.domain.exception.BusinessException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    override fun postJson(path: String, body: String): String {
        val requestBuilder = Request.Builder()
            .url(baseUrl + path)
            .post(body.toRequestBody(jsonMediaType))
        addAuthHeader(requestBuilder)
        return executeForString(requestBuilder.build(), "POST $path")
    }

    override fun postJsonNullable(path: String, body: String): String? {
        val requestBuilder = Request.Builder()
            .url(baseUrl + path)
            .post(body.toRequestBody(jsonMediaType))
        addAuthHeader(requestBuilder)

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 404) return null
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw httpFailure(response.code, responseBody)
                }
                return responseBody ?: ""
            }
        } catch (e: BusinessException) {
            throw e
        } catch (e: IOException) {
            throw BusinessException.wrap("POST $path", e)
        }
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

    override fun getBytes(path: String): ByteArray? {
        val requestBuilder = Request.Builder()
            .url(baseUrl + path)
            .get()
        addAuthHeader(requestBuilder)

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 404 || response.code == 204) return null
                if (!response.isSuccessful) {
                    throw httpFailure(response.code)
                }
                return response.body?.bytes()
            }
        } catch (e: BusinessException) {
            throw e
        } catch (e: IOException) {
            throw BusinessException.wrap("GET $path", e)
        }
    }

    override fun putBytes(path: String, data: ByteArray): Boolean {
        val requestBuilder = Request.Builder()
            .url(baseUrl + path)
            .put(data.toRequestBody(octetMediaType))
        addAuthHeader(requestBuilder)

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw httpFailure(response.code)
                }
                val responseBody = response.body?.string() ?: return false
                return responseBody.contains("\"success\":true") ||
                       responseBody.contains("\"success\": true")
            }
        } catch (e: BusinessException) {
            throw e
        } catch (e: IOException) {
            throw BusinessException.wrap("PUT $path", e)
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

    /** Os cabeçalhos de uma chamada autenticada: o token, o cliente e a transação remota corrente. */
    private fun addAuthHeader(builder: Request.Builder) {
        builder.header(CLIENT_HEADER, clientId)
        currentTxId()?.let { builder.header(TX_HEADER, it) }
        val supplier = accessTokenSupplier
        if (supplier != null) {
            val token = supplier()
            if (token != null) {
                builder.header("Authorization", "Bearer $token")
            }
        }
    }
}
