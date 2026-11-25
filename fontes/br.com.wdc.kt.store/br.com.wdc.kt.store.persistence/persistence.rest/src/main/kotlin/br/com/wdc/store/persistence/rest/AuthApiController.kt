package br.com.wdc.store.persistence.rest

import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.RestResponse
import br.com.wdc.store.domain.security.AuthResult
import br.com.wdc.store.domain.security.AuthenticationService
import br.com.wdc.store.persistence.rest.doc.RepositoryApiDocs

/**
 * HTTP adapter for the [AuthenticationService]. Public routes:
 *
 * - `POST /api/auth/challenge` — starts a user's sign-in: a nonce and the parameters of the user's SCRAM verifier
 * - `POST /api/auth/login` — ends it with the client's SCRAM proof; the answer carries the server's signature
 * - `POST /api/auth/refresh` — renews the access token
 * - `POST /api/auth/logout` — ends the session
 */
internal object AuthApiController {

    class ChallengeRequest(val userName: String? = null)

    class LoginRequest(val userName: String? = null, val proof: String? = null, val nonce: String? = null)

    class RefreshRequest(val refreshToken: String? = null)

    /** [requestsPerMinute]: challenge, login and refresh per client address and minute (`0` = no limit). */
    fun register(routes: RestApi.Routes, authService: AuthenticationService, requestsPerMinute: Int = 60) {
        val limit = AuthRateLimit(requestsPerMinute)
        // a POST: the user name goes in the body, not in an address that logs keep
        routes.post("/api/auth/challenge", doc = RepositoryApiDocs.authDoc("challenge")) { call ->
            limit.check(call.http.ip())
            val userName = call.bodyOrNull<ChallengeRequest>()?.userName
                ?: return@post RestResponse.status(400, mapOf("error" to "Missing required field: userName"))
            val result = authService.challenge(userName)
            linkedMapOf(
                "nonce" to result.nonce,
                "salt" to result.salt,
                "iterations" to result.iterations,
                "expiresAt" to result.expiresAt.toString(),
            )
        }

        routes.post("/api/auth/login", doc = RepositoryApiDocs.authDoc("login")) { call ->
            limit.check(call.http.ip())
            val body = call.bodyOrNull<LoginRequest>() ?: LoginRequest()
            if (body.userName == null || body.proof == null || body.nonce == null) {
                return@post RestResponse.status(400, mapOf("error" to "Missing required fields: userName, proof, nonce"))
            }
            // too many failures for the user: TooManyRequestsException, 429 with Retry-After
            val result = authService.login(body.userName, body.proof, body.nonce)
                ?: return@post RestResponse.status(401, mapOf("error" to "Invalid credentials"))
            tokens(result) + ("serverSignature" to result.serverSignature)
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
