package br.com.wdc.shopping.persistence.client

import br.com.wdc.shopping.domain.exception.BusinessException
import br.com.wdc.shopping.domain.security.CryptoProvider
import kotlinx.datetime.Instant
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class RestAuthClient(private val config: RestConfig) {

    var accessToken: String? = null
        private set
    var refreshToken: String? = null
        private set
    var publicKeyBase64: String? = null
        private set
    var expiresAtEpochSecond: Long = 0
        private set

    val isAuthenticated: Boolean
        get() = accessToken != null

    fun login(userName: String, passwordHash: String) {
        // 1. Get challenge (nonce)
        val challengeResponse = config.getJson("/api/auth/challenge")
        val nonce = challengeResponse.string("nonce")

        // 2. Compute HMAC: key=passwordHash, data=userName+nonce
        val digest = computeHmac(passwordHash, userName + nonce)

        // 3. Send login
        val loginBody = mapOf(
            "userName" to userName,
            "digest" to digest,
            "nonce" to nonce
        )

        val loginResponse = config.postJsonPublic("/api/auth/login", loginBody)

        this.accessToken = loginResponse.string("accessToken")
        this.refreshToken = loginResponse.string("refreshToken")
        this.publicKeyBase64 = loginResponse.string("publicKey")
        this.expiresAtEpochSecond = Instant.parse(loginResponse.string("expiresAt")).epochSeconds
    }

    fun refresh() {
        val rt = refreshToken ?: throw BusinessException("No refresh token available — login first")

        val body = mapOf("refreshToken" to rt)
        val response = config.postJsonPublic("/api/auth/refresh", body)

        this.accessToken = response.string("accessToken")
        this.refreshToken = response.string("refreshToken")
        this.publicKeyBase64 = response.string("publicKey")
        this.expiresAtEpochSecond = Instant.parse(response.string("expiresAt")).epochSeconds
    }

    fun logout() {
        if (accessToken != null) {
            try {
                config.postJsonWithAuth("/api/auth/logout", emptyMap(), accessToken!!)
            } catch (_: Exception) {
                // Ignore network errors on logout
            }
        }
        clearTokens()
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun encryptPassword(plainPassword: String): String {
        val pk = publicKeyBase64 ?: throw BusinessException("No public key available — login first")
        val crypto = CryptoProvider.BEAN.get()
        val encrypted = crypto.rsaEncryptOaep(pk, plainPassword.encodeToByteArray())
        return Base64.encode(encrypted)
    }

    internal fun setTokens(
        accessToken: String,
        refreshToken: String,
        publicKeyBase64: String,
        expiresAtEpochSecond: Long
    ) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.publicKeyBase64 = publicKeyBase64
        this.expiresAtEpochSecond = expiresAtEpochSecond
    }

    internal fun clearTokens() {
        this.accessToken = null
        this.refreshToken = null
        this.publicKeyBase64 = null
        this.expiresAtEpochSecond = 0
    }

    private fun computeHmac(key: String, data: String): String {
        val crypto = CryptoProvider.BEAN.get()
        val hash = crypto.hmacSha256(
            key.encodeToByteArray(),
            data.encodeToByteArray()
        )
        return bytesToHex(hash)
    }
}
