package br.com.wdc.store.persistence.client

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.domain.exception.UnauthenticatedException
import br.com.wdc.store.domain.exception.BusinessException
import br.com.wdc.store.domain.security.AuthResult
import br.com.wdc.store.domain.security.ChallengeResult
import br.com.wdc.store.domain.security.CryptoProvider
import br.com.wdc.store.domain.security.Scram
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Instant

/**
 * The client side of `/api/auth`, and the session it holds: the tokens every authenticated call carries.
 *
 * Signing in is SCRAM-SHA-256 ([Scram]): [challenge] gets the user's salt, iteration count and a nonce; the client
 * proves it knows the password ([Scram.respond]) and [login] sends the proof; the server answers with its own
 * signature, which [login] by password checks before keeping the session.
 */
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

    /** Starts [userName]'s sign-in. */
    fun challenge(userName: String): ChallengeResult {
        val body = config.toJson { out ->
            out.beginObject()
            out.name("userName").value(userName)
            out.endObject()
        }
        val input = config.postJsonPublic("/api/auth/challenge", body)
        var nonce = ""
        var salt = ""
        var iterations = 0
        var expiresAt = Instant.DISTANT_PAST
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "nonce" -> nonce = input.nextString()
                "salt" -> salt = input.nextString()
                "iterations" -> iterations = input.nextLong().toInt()
                "expiresAt" -> expiresAt = Instant.parse(input.nextString())
                else -> input.skipValue()
            }
        }
        input.endObject()
        return ChallengeResult(nonce, salt, iterations, expiresAt)
    }

    /**
     * Ends the sign-in with a SCRAM [proof] already computed, and keeps the session. `null` when the server refuses
     * it (401). The caller checks the server's signature in the result.
     */
    fun login(userName: String, proof: String, nonce: String): AuthResult? {
        val body = config.toJson { out ->
            out.beginObject()
            out.name("userName").value(userName)
            out.name("proof").value(proof)
            out.name("nonce").value(nonce)
            out.endObject()
        }
        val result = try {
            readAuthResult(config.postJsonPublic("/api/auth/login", body))
        } catch (_: UnauthenticatedException) {
            return null
        }
        keep(result)
        return result
    }

    /**
     * Signs in with the password as the user typed it: the whole exchange. `null` when the server refuses the
     * credentials; [BusinessException] when the server's signature is not the expected one — it does not hold the
     * user's verifier, so it is not the real server — and then no session is kept.
     */
    fun login(userName: String, password: String): AuthResult? {
        val challenge = challenge(userName)
        val response = Scram.current().respond(userName, password, challenge)
        val result = login(userName, response.proof, challenge.nonce) ?: return null
        if (!response.serverIsAuthentic(result.serverSignature)) {
            clearTokens()
            throw BusinessException("The server could not prove it holds the user's credentials")
        }
        return result
    }

    /** Renews the session with the refresh token it holds. */
    fun refresh() {
        val rt = refreshToken ?: throw BusinessException("No refresh token available — login first")
        refreshWith(rt) ?: throw UnauthenticatedException("Invalid or expired refresh token")
    }

    /** Renews the session of [refreshToken] and keeps it; `null` when the server refuses it (401). */
    fun refreshWith(refreshToken: String): AuthResult? {
        val body = config.toJson { out ->
            out.beginObject()
            out.name("refreshToken").value(refreshToken)
            out.endObject()
        }
        val result = try {
            readAuthResult(config.postJsonPublic("/api/auth/refresh", body))
        } catch (_: UnauthenticatedException) {
            return null
        }
        keep(result)
        return result
    }

    fun logout() {
        if (accessToken != null) {
            try {
                // the server revokes the session by the refresh token; without it, it would only answer success
                val body = config.toJson { out ->
                    out.beginObject()
                    refreshToken?.let { out.name("refreshToken").value(it) }
                    out.endObject()
                }
                config.postJsonWithAuth("/api/auth/logout", body, accessToken!!)
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

    private fun keep(result: AuthResult) {
        setTokens(result.accessToken, result.refreshToken, result.publicKey, result.expiresAt.epochSeconds, result.intentSignKey)
    }

    private fun readAuthResult(input: ExtensibleObjectInput): AuthResult {
        var userId = 0L
        var accessToken = ""
        var refreshToken = ""
        var expiresAt = Instant.DISTANT_PAST
        var publicKey = ""
        var intentSignKey = ""
        var serverSignature = ""
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "userId" -> userId = input.nextLong()
                "accessToken" -> accessToken = input.nextString()
                "refreshToken" -> refreshToken = input.nextString()
                "expiresAt" -> expiresAt = Instant.parse(input.nextString())
                "publicKey" -> publicKey = input.nextString()
                "intentSignKey" -> intentSignKey = input.nextString()
                "serverSignature" -> serverSignature = input.nextString()
                else -> input.skipValue()
            }
        }
        input.endObject()
        return AuthResult(userId, accessToken, refreshToken, expiresAt, publicKey, intentSignKey, serverSignature)
    }
}
