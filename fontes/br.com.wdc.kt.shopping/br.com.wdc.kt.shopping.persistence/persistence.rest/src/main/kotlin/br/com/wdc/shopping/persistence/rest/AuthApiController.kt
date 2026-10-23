package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.RestResponse
import br.com.wdc.shopping.domain.security.AuthResult
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.persistence.rest.doc.RepositoryApiDocs

/**
 * Adaptador HTTP para o [AuthenticationService]. Rotas públicas:
 *
 * - `GET /api/auth/challenge` — gera nonce para login
 * - `POST /api/auth/login` — autentica via HMAC challenge-response
 * - `POST /api/auth/refresh` — renova access token
 * - `POST /api/auth/logout` — encerra sessão
 */
internal object AuthApiController {

    class LoginRequest(val userName: String? = null, val digest: String? = null, val nonce: String? = null)

    class RefreshRequest(val refreshToken: String? = null)

    fun register(routes: RestApi.Routes, authService: AuthenticationService) {
        routes.get("/api/auth/challenge", doc = RepositoryApiDocs.authDoc("challenge")) { _ ->
            val result = authService.challenge()
            mapOf("nonce" to result.nonce, "expiresAt" to result.expiresAt.toString())
        }

        routes.post("/api/auth/login", doc = RepositoryApiDocs.authDoc("login")) { call ->
            val body = call.bodyOrNull<LoginRequest>() ?: LoginRequest()
            if (body.userName == null || body.digest == null || body.nonce == null) {
                return@post RestResponse.status(400, mapOf("error" to "Missing required fields: userName, digest, nonce"))
            }
            val result = authService.login(body.userName, body.digest, body.nonce)
                ?: return@post RestResponse.status(401, mapOf("error" to "Invalid credentials"))
            tokens(result)
        }

        routes.post("/api/auth/refresh", doc = RepositoryApiDocs.authDoc("refresh")) { call ->
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
