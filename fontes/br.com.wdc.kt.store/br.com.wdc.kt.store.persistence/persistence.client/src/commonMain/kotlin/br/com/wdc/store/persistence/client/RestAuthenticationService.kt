package br.com.wdc.store.persistence.client

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.store.domain.security.AuthResult
import br.com.wdc.store.domain.security.AuthenticationService
import br.com.wdc.store.domain.security.ChallengeResult
import br.com.wdc.store.domain.security.SecurityContext
import br.com.wdc.store.domain.security.SessionListener

/** The [AuthenticationService] of a client: the server's `/api/auth`, through [RestAuthClient]. */
class RestAuthenticationService(private val config: RestConfig) : AuthenticationService {

    private val authClient = RestAuthClient(config)

    init {
        config.setAuthClientInstance(authClient)
    }

    override suspend fun challenge(userName: String): ChallengeResult = authClient.challenge(userName)

    /** `null` when the server refuses the proof (401); too many failures: `TooManyRequestsException` (429). */
    override suspend fun login(userName: String, proof: String, nonce: String): AuthResult? =
        authClient.login(userName, proof, nonce)

    override fun refresh(refreshToken: String): AuthResult? = authClient.refreshWith(refreshToken)

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

    override fun signOut() = authClient.logout()

    override fun setSessionListener(listener: SessionListener?) {
        authClient.sessionListener = listener
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
