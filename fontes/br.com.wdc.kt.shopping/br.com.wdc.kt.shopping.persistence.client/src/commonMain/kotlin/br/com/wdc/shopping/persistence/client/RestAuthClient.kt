package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.shopping.domain.exception.BusinessException
import br.com.wdc.shopping.domain.security.CryptoProvider
import kotlin.time.Instant
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class RestAuthClient(private val config: RestConfig) {

    var accessToken: String? = null
        private set
    var refreshToken: String? = null
        private set
    var publicKeyBase64: String? = null
        private set
    var intentSignSecret: String? = null
        private set
    var expiresAtEpochSecond: Long = 0
        private set

    val isAuthenticated: Boolean
        get() = accessToken != null

    fun login(userName: String, passwordHash: String) {
        // 1. Get challenge (nonce)
        val challengeInput = config.getJson("/api/auth/challenge")
        val nonce = readStringField(challengeInput, "nonce")

        // 2. Compute HMAC: key=passwordHash, data=userName+nonce
        val digest = computeHmac(passwordHash, userName + nonce)

        // 3. Send login
        val loginBody = config.toJson { out ->
            out.beginObject()
            out.name("userName").value(userName)
            out.name("digest").value(digest)
            out.name("nonce").value(nonce)
            out.endObject()
        }

        val loginInput = config.postJsonPublic("/api/auth/login", loginBody)
        readAuthTokens(loginInput)
    }

    fun refresh() {
        val rt = refreshToken ?: throw BusinessException("No refresh token available — login first")

        val body = config.toJson { out ->
            out.beginObject()
            out.name("refreshToken").value(rt)
            out.endObject()
        }
        val input = config.postJsonPublic("/api/auth/refresh", body)
        readAuthTokens(input)
    }

    fun logout() {
        if (accessToken != null) {
            try {
                config.postJsonWithAuth("/api/auth/logout", "{}", accessToken!!)
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
        expiresAtEpochSecond: Long,
        intentSignSecret: String? = null
    ) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.publicKeyBase64 = publicKeyBase64
        this.expiresAtEpochSecond = expiresAtEpochSecond
        if (intentSignSecret != null) {
            this.intentSignSecret = intentSignSecret
        }
    }

    fun clearTokens() {
        this.accessToken = null
        this.refreshToken = null
        this.publicKeyBase64 = null
        this.intentSignSecret = null
        this.expiresAtEpochSecond = 0
    }

    private fun readAuthTokens(input: ExtensibleObjectInput) {
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "accessToken" -> this.accessToken = input.nextString()
                "refreshToken" -> this.refreshToken = input.nextString()
                "publicKey" -> this.publicKeyBase64 = input.nextString()
                "intentSignKey" -> this.intentSignSecret = input.nextString()
                "expiresAt" -> this.expiresAtEpochSecond = Instant.parse(input.nextString()).epochSeconds
                else -> input.skipValue()
            }
        }
        input.endObject()
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

private fun readStringField(input: ExtensibleObjectInput, fieldName: String): String {
    var result = ""
    input.beginObject()
    while (input.hasNext()) {
        when (input.nextName()) {
            fieldName -> result = input.nextString()
            else -> input.skipValue()
        }
    }
    input.endObject()
    return result
}
