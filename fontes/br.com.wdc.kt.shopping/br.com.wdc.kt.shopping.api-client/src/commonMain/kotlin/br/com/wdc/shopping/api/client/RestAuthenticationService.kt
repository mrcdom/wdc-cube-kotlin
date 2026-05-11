package br.com.wdc.shopping.api.client

import br.com.wdc.shopping.domain.exception.BusinessException
import br.com.wdc.shopping.domain.security.AuthResult
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.security.ChallengeResult
import br.com.wdc.shopping.domain.security.SecurityContext
import kotlinx.datetime.Instant

class RestAuthenticationService(private val config: RestConfig) : AuthenticationService {

    private val authClient = RestAuthClient(config)

    init {
        config.setAuthClientInstance(authClient)
    }

    override fun challenge(): ChallengeResult {
        val json = config.getJson("/api/auth/challenge")
        return ChallengeResult(
            nonce = json.string("nonce"),
            expiresAt = Instant.parse(json.string("expiresAt"))
        )
    }

    override fun login(userName: String, digest: String, nonce: String): AuthResult? {
        val body = mapOf(
            "userName" to userName,
            "digest" to digest,
            "nonce" to nonce
        )

        val response = try {
            config.postJsonPublic("/api/auth/login", body)
        } catch (e: BusinessException) {
            if (e.message?.contains("401") == true) return null
            throw e
        }

        val result = parseAuthResult(response)

        authClient.setTokens(
            result.accessToken,
            result.refreshToken,
            result.publicKey,
            result.expiresAt.epochSeconds
        )

        return result
    }

    override fun refresh(refreshToken: String): AuthResult? {
        val body = mapOf("refreshToken" to refreshToken)

        val response = try {
            config.postJsonPublic("/api/auth/refresh", body)
        } catch (e: BusinessException) {
            if (e.message?.contains("401") == true) return null
            throw e
        }

        val result = parseAuthResult(response)

        authClient.setTokens(
            result.accessToken,
            result.refreshToken,
            result.publicKey,
            result.expiresAt.epochSeconds
        )

        return result
    }

    override fun logout(refreshToken: String) {
        try {
            val body = mapOf("refreshToken" to refreshToken)
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

    private fun parseAuthResult(json: Map<String, Any?>): AuthResult {
        return AuthResult(
            userId = json.long("userId"),
            accessToken = json.string("accessToken"),
            refreshToken = json.string("refreshToken"),
            expiresAt = Instant.parse(json.string("expiresAt")),
            publicKey = json.string("publicKey")
        )
    }
}
