package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.shopping.domain.exception.BusinessException
import br.com.wdc.shopping.domain.security.AuthResult
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.security.ChallengeResult
import br.com.wdc.shopping.domain.security.SecurityContext
import kotlin.time.Instant

class RestAuthenticationService(private val config: RestConfig) : AuthenticationService {

    private val authClient = RestAuthClient(config)

    init {
        config.setAuthClientInstance(authClient)
    }

    override fun challenge(): ChallengeResult {
        val input = config.getJson("/api/auth/challenge")
        var nonce = ""
        var expiresAt = Instant.DISTANT_PAST
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "nonce" -> nonce = input.nextString()
                "expiresAt" -> expiresAt = Instant.parse(input.nextString())
                else -> input.skipValue()
            }
        }
        input.endObject()
        return ChallengeResult(nonce, expiresAt)
    }

    override fun login(userName: String, digest: String, nonce: String): AuthResult? {
        val body = config.toJson { out ->
            out.beginObject()
            out.name("userName").value(userName)
            out.name("digest").value(digest)
            out.name("nonce").value(nonce)
            out.endObject()
        }

        val input = try {
            config.postJsonPublic("/api/auth/login", body)
        } catch (e: BusinessException) {
            if (e.message?.contains("401") == true) return null
            throw e
        }

        val result = parseAuthResult(input)

        authClient.setTokens(
            result.accessToken,
            result.refreshToken,
            result.publicKey,
            result.expiresAt.epochSeconds,
            result.intentSignKey
        )

        return result
    }

    override fun refresh(refreshToken: String): AuthResult? {
        val body = config.toJson { out ->
            out.beginObject()
            out.name("refreshToken").value(refreshToken)
            out.endObject()
        }

        val input = try {
            config.postJsonPublic("/api/auth/refresh", body)
        } catch (e: BusinessException) {
            if (e.message?.contains("401") == true) return null
            throw e
        }

        val result = parseAuthResult(input)

        authClient.setTokens(
            result.accessToken,
            result.refreshToken,
            result.publicKey,
            result.expiresAt.epochSeconds,
            result.intentSignKey
        )

        return result
    }

    override fun logout(refreshToken: String) {
        try {
            val body = config.toJson { out ->
                out.beginObject()
                out.name("refreshToken").value(refreshToken)
                out.endObject()
            }
            config.postJsonPublic("/api/auth/logout", body)
        } catch (_: Exception) {
            // Ignore network errors on logout
        }
        authClient.clearTokens()
    }

    override fun resolveToken(jwtToken: String): SecurityContext? {
        // Token resolution is server-side only.
        return null
    }

    private fun parseAuthResult(input: ExtensibleObjectInput): AuthResult {
        var userId = 0L
        var accessToken = ""
        var refreshToken = ""
        var expiresAt = Instant.DISTANT_PAST
        var publicKey = ""
        var intentSignKey = ""

        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "userId" -> userId = input.nextLong()
                "accessToken" -> accessToken = input.nextString()
                "refreshToken" -> refreshToken = input.nextString()
                "expiresAt" -> expiresAt = Instant.parse(input.nextString())
                "publicKey" -> publicKey = input.nextString()
                "intentSignKey" -> intentSignKey = input.nextString()
                else -> input.skipValue()
            }
        }
        input.endObject()

        return AuthResult(userId, accessToken, refreshToken, expiresAt, publicKey, intentSignKey)
    }

    override fun writeAuthState(out: ExtensibleObjectOutput) {
        out.beginObject()
        authClient.accessToken?.let { out.name("accessToken").value(it) }
        authClient.refreshToken?.let { out.name("refreshToken").value(it) }
        authClient.publicKeyBase64?.let { out.name("publicKeyBase64").value(it) }
        authClient.intentSignSecret?.let { out.name("intentSignSecret").value(it) }
        if (authClient.expiresAtEpochSecond > 0) {
            out.name("expiresAt").value(authClient.expiresAtEpochSecond)
        }
        out.endObject()
    }

    override fun readAuthState(input: ExtensibleObjectInput) {
        var accessToken: String? = null
        var refreshToken: String? = null
        var publicKeyBase64: String? = null
        var intentSignSecret: String? = null
        var expiresAt = 0L

        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "accessToken" -> accessToken = InputCoerceUtils.asString(input)
                "refreshToken" -> refreshToken = InputCoerceUtils.asString(input)
                "publicKeyBase64" -> publicKeyBase64 = InputCoerceUtils.asString(input)
                "intentSignSecret" -> intentSignSecret = InputCoerceUtils.asString(input)
                "expiresAt" -> expiresAt = InputCoerceUtils.asLong(input) ?: 0L
                else -> input.skipValue()
            }
        }
        input.endObject()

        if (accessToken != null && refreshToken != null && publicKeyBase64 != null) {
            authClient.setTokens(accessToken, refreshToken, publicKeyBase64, expiresAt, intentSignSecret)
        }
    }
}
