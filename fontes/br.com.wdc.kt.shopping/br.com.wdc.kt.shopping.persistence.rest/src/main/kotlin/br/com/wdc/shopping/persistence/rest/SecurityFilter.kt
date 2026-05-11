package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.shopping.domain.exception.AccessDeniedException
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.security.SecurityContextHolder
import io.javalin.config.JavalinConfig
import io.javalin.http.Context

/**
 * Filtro de segurança HTTP para endpoints `/api/repo/`.
 *
 * Extrai o Bearer token, delega validação ao [AuthenticationService],
 * e popula o [SecurityContextHolder] para a requisição corrente.
 */
class SecurityFilter(private val authService: AuthenticationService) {

    companion object {
        private val LOG = Log.getLogger(SecurityFilter::class.java)
        private const val BEARER_PREFIX = "Bearer "
    }

    /**
     * Handler a ser registrado como `before("/api/repo/{path}")`.
     *
     * Rotas de imagem de produto são públicas (catálogo) e não exigem autenticação.
     */
    fun handle(ctx: Context) {
        // Skip CORS preflight requests (OPTIONS)
        if (ctx.method().name().equals("OPTIONS", ignoreCase = true)) {
            return
        }

        if (isPublicRoute(ctx.path())) {
            return
        }

        val authHeader = ctx.header("Authorization")
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            ctx.status(401).json(mapOf("error" to "Missing or invalid Authorization header"))
            ctx.skipRemainingHandlers()
            return
        }

        val token = authHeader.substring(BEARER_PREFIX.length)
        val securityContext = authService.resolveToken(token)
        if (securityContext == null) {
            ctx.status(401).json(mapOf("error" to "Invalid or expired token"))
            ctx.skipRemainingHandlers()
            return
        }

        SecurityContextHolder.set(securityContext)
        LOG.debug("Authenticated request: user={} path={}", securityContext.userName, ctx.path())
    }

    private fun isPublicRoute(path: String): Boolean {
        return path.endsWith("/image")
    }
}
