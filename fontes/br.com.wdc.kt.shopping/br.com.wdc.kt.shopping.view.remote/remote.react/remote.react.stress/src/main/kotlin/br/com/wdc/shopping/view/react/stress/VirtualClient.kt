package br.com.wdc.shopping.view.react.stress

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import com.google.gson.reflect.TypeToken
import okhttp3.*
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Simulates a single browser client connecting to the remote.react skeleton via WebSocket.
 * Handles the full lifecycle: HTTP session creation, crypto handshake, event dispatch, and response parsing.
 */
class VirtualClient(
    private val clientId: Int,
    private val config: StressConfig,
    private val credentials: UserCredentials,
    private val metrics: MetricsCollector,
    private val httpClient: OkHttpClient,
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(VirtualClient::class.java)
        private val GSON: Gson = GsonBuilder()
            .serializeNulls()
            .setObjectToNumberStrategy(ToNumberPolicy.DOUBLE)
            .create()
        private val RESPONSE_TYPE = object : TypeToken<Map<String, Any?>>() {}
        private val random = SecureRandom()
    }

    private val requestIdGen = AtomicLong(1)

    // Session state
    private var appId: String = ""
    private var rsaExponent: BigInteger = BigInteger.ZERO
    private var rsaPublicKey: BigInteger = BigInteger.ZERO
    private var signature: String = ""
    private var aesKey: SecretKeySpec? = null
    private var aesIv: ByteArray = ByteArray(12)

    // WebSocket
    private var webSocket: WebSocket? = null
    private var connected = false
    private var lastResponse: Map<String, Any?>? = null
    private val responseLatch = CountDownLatch(1)
    private var pendingLatch: CountDownLatch? = null

    // View state (tracks current screen from server responses)
    private var currentUri: String = ""
    private var viewStates: MutableMap<String, Map<String, Any?>> = mutableMapOf()

    /**
     * Establishes the session: HTTP request for cookies, then WebSocket connection.
     * Returns true if connection was successful.
     */
    fun connect(): Boolean {
        val startTime = System.currentTimeMillis()
        try {
            // Step 1: GET index.html to obtain session cookies
            if (!fetchSessionCookies()) {
                LOG.error("[Client-{}] Failed to fetch session cookies", clientId)
                return false
            }

            // Step 2: Build crypto signature
            buildCryptoSignature()

            // Step 3: Connect WebSocket
            if (!connectWebSocket()) {
                LOG.error("[Client-{}] WebSocket connection failed", clientId)
                return false
            }

            val elapsed = System.currentTimeMillis() - startTime
            metrics.recordConnectionTime(elapsed)
            LOG.debug("[Client-{}] Connected in {}ms", clientId, elapsed)
            return true
        } catch (e: Exception) {
            LOG.error("[Client-{}] Connection failed: {}", clientId, e.message)
            return false
        }
    }

    /**
     * Sends an event to a view and waits for the server response.
     * Returns the response latency in milliseconds, or -1 on error.
     */
    fun sendEvent(viewId: String, eventCode: Int, formData: Map<String, Any?> = emptyMap()): Long {
        val requestId = requestIdGen.getAndIncrement()
        val request = buildRequest(requestId, viewId, eventCode, formData)
        val json = GSON.toJson(request)

        pendingLatch = CountDownLatch(1)
        val startTime = System.currentTimeMillis()

        webSocket?.send(json) ?: return -1

        // Wait for response (timeout 15s)
        val gotResponse = pendingLatch!!.await(15, TimeUnit.SECONDS)
        val latency = System.currentTimeMillis() - startTime

        if (!gotResponse) {
            LOG.warn("[Client-{}] Timeout waiting for response to event {}:{}", clientId, viewId, eventCode)
            return -1
        }

        return latency
    }

    /**
     * Disconnects the WebSocket.
     */
    fun disconnect() {
        webSocket?.close(1000, "stress test complete")
        webSocket = null
        connected = false
    }

    fun isConnected(): Boolean = connected

    fun getCurrentUri(): String = currentUri

    fun getViewState(instanceId: String): Map<String, Any?> = viewStates[instanceId] ?: emptyMap()

    // :: Private — HTTP session

    private fun fetchSessionCookies(): Boolean {
        val url = "${config.baseUrl}${config.contextPath}/index.html"
        val request = Request.Builder().url(url).get().build()
        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            LOG.error("[Client-{}] HTTP {} from {}", clientId, response.code, url)
            response.close()
            return false
        }

        // Extract cookies from response
        val cookies = response.headers("Set-Cookie")
        for (cookie in cookies) {
            when {
                cookie.startsWith("app_id=") -> {
                    appId = cookie.substringAfter("app_id=").substringBefore(";")
                }
                cookie.startsWith("app_skey=") -> {
                    val skey = cookie.substringAfter("app_skey=").substringBefore(";")
                    val parts = skey.split(":")
                    if (parts.size == 2) {
                        rsaExponent = BigInteger(parts[0], 36)
                        rsaPublicKey = BigInteger(parts[1], 36)
                    }
                }
            }
        }

        response.close()

        if (appId.isBlank() || rsaPublicKey == BigInteger.ZERO) {
            LOG.error("[Client-{}] Missing cookies: appId={}, rsaKey={}", clientId, appId, rsaPublicKey != BigInteger.ZERO)
            return false
        }

        return true
    }

    // :: Private — Crypto

    private fun buildCryptoSignature() {
        // Generate random password (16 bytes)
        val password = ByteArray(16)
        random.nextBytes(password)
        val passwordB64 = Base64.getEncoder().encodeToString(password)

        // Generate random salt and IV
        val salt = ByteArray(16)
        random.nextBytes(salt)
        aesIv = ByteArray(12)
        random.nextBytes(aesIv)

        // Derive AES-256 key using PBKDF2
        val spec = PBEKeySpec(passwordB64.toCharArray(), salt, 250_000, 256)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = keyFactory.generateSecret(spec)
        aesKey = SecretKeySpec(secretKey.encoded, "AES")

        // Encrypt password with RSA (raw BigInteger modular exponentiation)
        val passwordBytes = passwordB64.toByteArray(Charsets.UTF_8)
        val passwordBigInt = BigInteger(1, passwordBytes)
        val encrypted = passwordBigInt.modPow(rsaExponent, rsaPublicKey)
        val encryptedBase36 = encrypted.toString(36)

        // Build signature: {encrypted_password}.{base64url_salt}.{base64url_iv}
        val saltB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(salt)
        val ivB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(aesIv)
        signature = "$encryptedBase36.$saltB64.$ivB64"
    }

    fun cipherField(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, aesIv)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    // :: Private — WebSocket

    private fun connectWebSocket(): Boolean {
        val wsUrl = config.baseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://") +
            "${config.contextPath}/dispatcher/${appId}"

        val request = Request.Builder()
            .url(wsUrl)
            .header("Cookie", "app_signature=$signature")
            .header("Sec-WebSocket-Protocol", "wdc")
            .build()

        val latch = CountDownLatch(1)

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                latch.countDown()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleResponse(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                LOG.error("[Client-{}] WebSocket failure: {}", clientId, t.message)
                connected = false
                latch.countDown()
                pendingLatch?.countDown()
            }
        })

        // Wait up to 10s for connection
        latch.await(10, TimeUnit.SECONDS)

        if (!connected) return false

        // Send initial handshake message
        val initMsg = buildInitialMessage()
        webSocket?.send(GSON.toJson(initMsg))

        // Wait for first response (initial view state)
        pendingLatch = CountDownLatch(1)
        return pendingLatch!!.await(10, TimeUnit.SECONDS)
    }

    private fun buildInitialMessage(): Map<String, Any?> {
        return mapOf(
            "requestId" to requestIdGen.getAndIncrement(),
            "ping" to true,
            "secret" to signature,
            "path" to "",
        )
    }

    private fun buildRequest(
        requestId: Long,
        viewId: String,
        eventCode: Int,
        formData: Map<String, Any?>,
    ): Map<String, Any?> {
        val request = mutableMapOf<String, Any?>(
            "requestId" to requestId,
            "event" to listOf("$viewId:$eventCode"),
        )
        if (formData.isNotEmpty()) {
            request[viewId] = formData
        }
        return request
    }

    private fun handleResponse(text: String) {
        try {
            val response: Map<String, Any?> = GSON.fromJson(text, RESPONSE_TYPE.type)

            // Update URI if present
            val uri = response["uri"] as? String
            if (!uri.isNullOrBlank()) {
                currentUri = uri
            }

            // Update view states
            @Suppress("UNCHECKED_CAST")
            val states = response["states"] as? List<Map<String, Any?>>
            if (states != null) {
                for (state in states) {
                    val id = state["id"] as? String ?: continue
                    viewStates[id] = state
                }
            }

            lastResponse = response
        } catch (e: Exception) {
            LOG.warn("[Client-{}] Failed to parse response: {}", clientId, e.message)
        } finally {
            pendingLatch?.countDown()
        }
    }
}
