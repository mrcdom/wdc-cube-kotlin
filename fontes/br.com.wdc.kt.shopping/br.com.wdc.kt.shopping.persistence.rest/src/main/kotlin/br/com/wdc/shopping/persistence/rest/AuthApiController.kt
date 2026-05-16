package br.com.wdc.shopping.persistence.rest

import br.com.wdc.shopping.domain.security.AuthenticationService
import io.javalin.config.JavalinConfig
import io.javalin.http.Context

/**
 * Adaptador HTTP para o [AuthenticationService].
 *
 * Endpoints:
 * - `GET /api/auth/challenge` — gera nonce para login
 * - `POST /api/auth/login` — autentica via HMAC challenge-response
 * - `POST /api/auth/refresh` — renova access token
 * - `POST /api/auth/logout` — encerra sessão
 */
class AuthApiController(private val authService: AuthenticationService) {

    fun configure(config: JavalinConfig) {
        config.routes.get("/api/auth/challenge", ::challenge)
        config.routes.post("/api/auth/login", ::login)
        config.routes.post("/api/auth/refresh", ::refresh)
        config.routes.post("/api/auth/logout", ::logout)
    }

    private fun challenge(ctx: Context) {
        val result = authService.challenge()
        json(ctx, mapOf(
            "nonce" to result.nonce,
            "expiresAt" to result.expiresAt.toString()
        ))
    }

    private fun login(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), com.google.gson.JsonObject::class.java)

        val userName = body.get("userName")?.asString
        val digest = body.get("digest")?.asString
        val nonce = body.get("nonce")?.asString

        if (userName == null || digest == null || nonce == null) {
            ctx.status(400).json(mapOf("error" to "Missing required fields: userName, digest, nonce"))
            return
        }

        val result = authService.login(userName, digest, nonce)
        if (result == null) {
            ctx.status(401).json(mapOf("error" to "Invalid credentials"))
            return
        }

        json(ctx, mapOf(
            "userId" to result.userId,
            "accessToken" to result.accessToken,
            "refreshToken" to result.refreshToken,
            "expiresAt" to result.expiresAt.toString(),
            "publicKey" to result.publicKey,
            "intentSignKey" to result.intentSignKey
        ))
    }

    private fun refresh(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), com.google.gson.JsonObject::class.java)

        val refreshToken = body.get("refreshToken")?.asString
        if (refreshToken == null) {
            ctx.status(400).json(mapOf("error" to "Missing refreshToken"))
            return
        }

        val result = authService.refresh(refreshToken)
        if (result == null) {
            ctx.status(401).json(mapOf("error" to "Invalid or expired refresh token"))
            return
        }

        json(ctx, mapOf(
            "userId" to result.userId,
            "accessToken" to result.accessToken,
            "refreshToken" to result.refreshToken,
            "expiresAt" to result.expiresAt.toString(),
            "publicKey" to result.publicKey,
            "intentSignKey" to result.intentSignKey
        ))
    }

    private fun logout(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), com.google.gson.JsonObject::class.java)
        val refreshToken = body.get("refreshToken")?.asString
        if (refreshToken != null) {
            authService.logout(refreshToken)
        }
        json(ctx, mapOf("success" to true))
    }

    companion object {
        private fun json(ctx: Context, obj: Any) {
            ctx.contentType("application/json")
            ctx.result(ApiGson.instance.toJson(obj))
        }
    }
}
