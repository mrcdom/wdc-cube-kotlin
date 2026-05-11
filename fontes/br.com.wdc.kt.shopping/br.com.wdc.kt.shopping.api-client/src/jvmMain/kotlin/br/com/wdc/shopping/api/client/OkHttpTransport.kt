@file:Suppress("UNCHECKED_CAST")

package br.com.wdc.shopping.api.client

import br.com.wdc.shopping.domain.exception.BusinessException
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpTransport(private val baseUrl: String) : HttpTransport {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val octetMediaType = "application/octet-stream".toMediaType()
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override var accessTokenSupplier: (() -> String?)? = null

    override fun postJson(path: String, body: Map<String, Any?>): Map<String, Any?> {
        val requestBuilder = Request.Builder()
            .url(baseUrl + path)
            .post(gson.toJson(body).toRequestBody(jsonMediaType))
        addAuthHeader(requestBuilder)

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw BusinessException("HTTP ${response.code}: $responseBody")
                }
                return gson.fromJson(responseBody, Map::class.java) as Map<String, Any?>
            }
        } catch (e: BusinessException) {
            throw e
        } catch (e: IOException) {
            throw BusinessException.wrap("POST $path", e)
        }
    }

    override fun postJsonNullable(path: String, body: Map<String, Any?>): Map<String, Any?>? {
        val requestBuilder = Request.Builder()
            .url(baseUrl + path)
            .post(gson.toJson(body).toRequestBody(jsonMediaType))
        addAuthHeader(requestBuilder)

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 404) return null
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw BusinessException("HTTP ${response.code}: $responseBody")
                }
                return gson.fromJson(responseBody, Map::class.java) as Map<String, Any?>
            }
        } catch (e: BusinessException) {
            throw e
        } catch (e: IOException) {
            throw BusinessException.wrap("POST $path", e)
        }
    }

    override fun postJsonPublic(path: String, body: Map<String, Any?>): Map<String, Any?> {
        val request = Request.Builder()
            .url(baseUrl + path)
            .post(gson.toJson(body).toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw BusinessException("HTTP ${response.code}: $responseBody")
                }
                return gson.fromJson(responseBody, Map::class.java) as Map<String, Any?>
            }
        } catch (e: BusinessException) {
            throw e
        } catch (e: IOException) {
            throw BusinessException.wrap("POST $path", e)
        }
    }

    override fun postJsonWithAuth(path: String, body: Map<String, Any?>, token: String): Map<String, Any?> {
        val request = Request.Builder()
            .url(baseUrl + path)
            .post(gson.toJson(body).toRequestBody(jsonMediaType))
            .header("Authorization", "Bearer $token")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw BusinessException("HTTP ${response.code}: $responseBody")
                }
                return gson.fromJson(responseBody, Map::class.java) as Map<String, Any?>
            }
        } catch (e: BusinessException) {
            throw e
        } catch (e: IOException) {
            throw BusinessException.wrap("POST $path", e)
        }
    }

    override fun getJson(path: String): Map<String, Any?> {
        val request = Request.Builder()
            .url(baseUrl + path)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw BusinessException("HTTP ${response.code}: $responseBody")
                }
                return gson.fromJson(responseBody, Map::class.java) as Map<String, Any?>
            }
        } catch (e: BusinessException) {
            throw e
        } catch (e: IOException) {
            throw BusinessException.wrap("GET $path", e)
        }
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
                    throw BusinessException("HTTP ${response.code}")
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
                    throw BusinessException("HTTP ${response.code}")
                }
                val responseBody = response.body?.string()
                val json = JsonParser.parseString(responseBody).asJsonObject
                return json.get("success").asBoolean
            }
        } catch (e: BusinessException) {
            throw e
        } catch (e: IOException) {
            throw BusinessException.wrap("PUT $path", e)
        }
    }

    private fun addAuthHeader(builder: Request.Builder) {
        val supplier = accessTokenSupplier
        if (supplier != null) {
            val token = supplier()
            if (token != null) {
                builder.header("Authorization", "Bearer $token")
            }
        }
    }
}
