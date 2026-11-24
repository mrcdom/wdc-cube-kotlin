package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.RestResponse
import br.com.wdc.shopping.domain.security.AuthResult
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.persistence.rest.doc.RepositoryApiDocs

/**
 * HTTP adapter for the [AuthenticationService]. Public routes:
 *
 * - `GET /api/auth/challenge` — generates a nonce for login
 * - `POST /api/auth/login` — authenticates via HMAC challenge-response
 * - `POST /api/auth/refresh` — renews the access token
 * - `POST /api/auth/logout` — ends the session
 */
internal object AuthApiController {

    class LoginRequest(val userName: String? = null, val digest: String? = null, val nonce: String? = null)

    class RefreshRequest(val refreshToken: String? = null)

    /** [requestsPerMinute]: challenge, login and refresh per client address and minute (`0` = no limit). */
    fun register(routes: RestApi.Routes, authService: AuthenticationService, requestsPerMinute: Int = 60) {
        val limit = AuthRateLimit(requestsPerMinute)
        routes.get("/api/auth/challenge", doc = RepositoryApiDocs.authDoc("challenge")) { call ->
            limit.check(call.http.ip())
            val result = authService.challenge()
            mapOf("nonce" to result.nonce, "expiresAt" to result.expiresAt.toString())
        }

        routes.post("/api/auth/login", doc = RepositoryApiDocs.authDoc("login")) { call ->
            limit.check(call.http.ip())
            val body = call.bodyOrNull<LoginRequest>() ?: LoginRequest()
            if (body.userName == null || body.digest == null || body.nonce == null) {
                return@post RestResponse.status(400, mapOf("error" to "Missing required fields: userName, digest, nonce"))
            }
            val result = authService.login(body.userName, body.digest, body.nonce)
                ?: return@post RestResponse.status(401, mapOf("error" to "Invalid credentials"))
            tokens(result)
        }

        routes.post("/api/auth/refresh", doc = RepositoryApiDocs.authDoc("refresh")) { call ->
            limit.check(call.http.ip())
            val refreshToken = call.bodyOrNull<RefreshRequest>()?.refreshToken
                ?: return@post RestResponse.status(400, mapOf("error" to "Missing refreshToken"))
            val result = authService.refresh(refreshToken)
                ?: return@post RestResponse.status(401, mapOf("error" to "Invalid or expired refresh token"))
            tokens(result)
        }

        routes.post("/api/auth/logout", doc = RepositoryApiDocs.authDoc("logout")) { call ->
            call.bodyOrNull<RefreshRequest>()?.refreshToken?.let(authService::logout)
            mapOf("success" to true)
        }
    }

    private fun tokens(result: AuthResult): Map<String, Any?> = linkedMapOf(
        "userId" to result.userId,
        "accessToken" to result.accessToken,
        "refreshToken" to result.refreshToken,
        "expiresAt" to result.expiresAt.toString(),
        "publicKey" to result.publicKey,
        "intentSignKey" to result.intentSignKey,
    )
}
