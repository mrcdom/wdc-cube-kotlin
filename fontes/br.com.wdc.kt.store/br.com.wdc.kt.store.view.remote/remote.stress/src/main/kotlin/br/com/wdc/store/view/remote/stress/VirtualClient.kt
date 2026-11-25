package br.com.wdc.store.view.remote.stress

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import com.google.gson.reflect.TypeToken
import okhttp3.*
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Simulates a single browser client connecting to the remote host via WebSocket.
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
        private val KEEP_ALIVE_EXECUTOR = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "stress-keepalive").apply { isDaemon = true }
        }
    }

    private val requestIdGen = AtomicLong(1)

    // Session state
    private var appId: String = ""
    private var rsaExponent: BigInteger = BigInteger.ZERO
    private var rsaPublicKey: BigInteger = BigInteger.ZERO
    private var signature: String = ""
    private var aesKey: SecretKeySpec? = null

    // WebSocket
    private var webSocket: WebSocket? = null
    private var connected = false
    private var lastResponse: Map<String, Any?>? = null
    private var pendingLatch: CountDownLatch? = null
    @Volatile private var pendingRequestId: Long = Long.MIN_VALUE
    @Volatile private var lastMessageTime: Long = 0

    // Keep-alive
    private var keepAliveTask: ScheduledFuture<*>? = null

    // Scopes (track current screen from server responses)
    private var currentUri: String = ""
    private var scopes: MutableMap<String, Map<String, Any?>> = mutableMapOf()

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

            // Step 3: Connect WebSocket and wait for Login view to appear in state
            if (!connectWebSocket()) {
                LOG.error("[Client-{}] WebSocket connection failed", clientId)
                return false
            }

            // Step 4: Send onStart event to Browser to trigger navigation
            val browserViewId = findViewByVid(Vid.BROWSER)
            if (browserViewId != null) {
                val latency = sendEvent(browserViewId, -1, mapOf("p.path" to "/"))
                if (latency < 0) {
                    LOG.error("[Client-{}] Start event failed (timeout)", clientId)
                    return false
                }
            }

            // Step 5: Wait for Login view (arrives via flushDirtyViews after navigation)
            if (!waitForView(Vid.LOGIN, 5000)) {
                LOG.error("[Client-{}] Login view not in state after onStart (views: {})", clientId, scopes.keys)
                return false
            }

            val elapsed = System.currentTimeMillis() - startTime
            metrics.recordConnectionTime(elapsed)
            LOG.debug("[Client-{}] Connected in {}ms (views: {})", clientId, elapsed, scopes.keys)
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

        pendingRequestId = requestId
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
     * Disconnects the WebSocket and cancels keep-alive.
     */
    fun disconnect() {
        keepAliveTask?.cancel(false)
        keepAliveTask = null
        webSocket?.close(1000, "stress test complete")
        webSocket = null
        connected = false
    }


    fun isConnected(): Boolean = connected

    fun getCurrentUri(): String = currentUri

    /**
     * Waits until a view with the given VID appears in tracked state AND server is idle.
     */
    fun waitForView(vid: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (findViewByVid(vid) != null) {
                // Wait until server stops sending (200ms idle)
                val idleStart = System.currentTimeMillis()
                while (System.currentTimeMillis() - lastMessageTime < 200
                    && System.currentTimeMillis() < deadline) {
                    Thread.sleep(50)
                }
                return true
            }
            Thread.sleep(50)
        }
        return findViewByVid(vid) != null
    }

    fun getScope(instanceId: String): Map<String, Any?> = scopes[instanceId] ?: emptyMap()

    /**
     * Finds the full instance ID (e.g. "c677cda52d14:1") for a given VID prefix (e.g. "c677cda52d14").
     * Returns null if no matching scope is currently tracked.
     */
    fun findViewByVid(vid: String): String? {
        return reachableViewIds().firstOrNull { it.startsWith("$vid:") }
    }

    /**
     * Instance IDs still attached to the Browser view, following every state field that
     * references another tracked view. The server announces the views it releases
     * (`releasedViews`), and they are dropped; this also skips any entry left behind.
     */
    private fun reachableViewIds(): Set<String> {
        val known = HashMap(scopes)
        val reachable = LinkedHashSet<String>()
        val pending = ArrayDeque<String>()
        known.keys.firstOrNull { it.startsWith("${Vid.BROWSER}:") }?.let(pending::add)
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (!reachable.add(id)) continue
            for ((name, value) in known[id] ?: continue) {
                if (name == "id") continue
                if (value is String && known.containsKey(value)) {
                    pending.add(value)
                } else if (value is List<*>) {
                    // a list of rows, each a view of its own ("<field>ViewIds")
                    value.filterIsInstance<String>().filter { known.containsKey(it) }.forEach(pending::add)
                }
            }
        }
        return reachable
    }

    /**
     * Returns the scope map for the view identified by VID prefix.
     */
    fun getScopeByVid(vid: String): Map<String, Any?> {
        val instanceId = findViewByVid(vid) ?: return emptyMap()
        return scopes[instanceId] ?: emptyMap()
    }

    /**
     * Returns all currently tracked instance IDs (for diagnostics).
     */
    fun getTrackedViewIds(): Set<String> = scopes.keys

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
        // Generate random password (12 bytes, base64url — same as the browser client)
        val password = ByteArray(12)
        random.nextBytes(password)
        val passwordB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(password)

        // Generate random salt (each ciphered value gets its own IV)
        val salt = ByteArray(16)
        random.nextBytes(salt)

        // Derive AES-256 key using PBKDF2
        val spec = PBEKeySpec(passwordB64.toCharArray(), salt, 250_000, 256)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = keyFactory.generateSecret(spec)
        aesKey = SecretKeySpec(secretKey.encoded, "AES")

        // Encrypt password with RSA (raw BigInteger modular exponentiation).
        // The server Base64-decodes the RSA payload to recover the PBKDF2 password.
        val passwordBytes = Base64.getEncoder().encode(passwordB64.toByteArray(Charsets.UTF_8))
        val passwordBigInt = BigInteger(1, passwordBytes)
        val encrypted = passwordBigInt.modPow(rsaExponent, rsaPublicKey)
        val encryptedBase36 = encrypted.toString(36)

        // Build signature: {encrypted_password}.{base64url_salt}
        val saltB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(salt)
        signature = "$encryptedBase36.$saltB64"
    }

    /** Ciphers as the browser does: a new random IV, in front of the ciphertext. */
    fun cipherField(plainText: String): String {
        val iv = ByteArray(12)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
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

        // Wait for initial response (accept any requestId)
        pendingRequestId = -1L
        pendingLatch = CountDownLatch(1)
        val gotResponse = pendingLatch!!.await(10, TimeUnit.SECONDS)

        if (gotResponse) {
            startKeepAlive()
        }
        return gotResponse
    }

    private fun buildInitialMessage(): Map<String, Any?> {
        return mapOf(
            "requestId" to -1L,
            "ping" to true,
            "secret" to signature,
            "path" to "/",
        )
    }

    private fun startKeepAlive() {
        val browserViewId = findViewByVid(Vid.BROWSER) ?: return
        keepAliveTask = KEEP_ALIVE_EXECUTOR.scheduleAtFixedRate({
            if (connected) {
                val request = buildRequest(requestIdGen.getAndIncrement(), browserViewId, 2, emptyMap())
                webSocket?.send(GSON.toJson(request))
            }
        }, 15, 15, TimeUnit.SECONDS)
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
        lastMessageTime = System.currentTimeMillis()
        try {
            LOG.debug("[Client-{}] Raw response ({} chars): {}", clientId, text.length, text)
            val response: Map<String, Any?> = GSON.fromJson(text, RESPONSE_TYPE.type)

            // Update URI if present
            val uri = response["uri"] as? String
            if (!uri.isNullOrBlank()) {
                currentUri = uri
            }

            // Update the scopes (merge — server sends only changed views)
            @Suppress("UNCHECKED_CAST")
            val received = response["scopes"] as? List<Map<String, Any?>>
            if (received != null) {
                for (scope in received) {
                    val id = scope["id"] as? String ?: continue
                    scopes[id] = scope
                }
                LOG.debug("[Client-{}] After merge, scopes keys: {}", clientId, scopes.keys)
            }

            // The views the server let go of: it never sends them again
            @Suppress("UNCHECKED_CAST")
            (response["releasedViews"] as? List<String>)?.forEach { scopes.remove(it) }

            lastResponse = response

            // Signal pending latch when response matches or exceeds our pending request
            val responseId = (response["requestId"] as? Number)?.toLong() ?: return
            if (responseId >= pendingRequestId) {
                pendingLatch?.countDown()
            }
        } catch (e: Exception) {
            LOG.warn("[Client-{}] Failed to parse response: {}", clientId, e.message)
            pendingLatch?.countDown()
        }
    }
}
