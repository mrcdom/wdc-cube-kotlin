package br.com.wdc.shopping.persistence.rest

import br.com.wdc.shopping.domain.exception.AccessDeniedException
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.security.SecurityContextHolder
import io.javalin.config.JavalinConfig

/**
 * Registra todos os endpoints REST da API de repositório no Javalin.
 *
 * Se o [AuthenticationService] estiver inicializado (via
 * `RepositoryBootstrap.initializeSecurity`), registra automaticamente
 * o filtro de segurança e os endpoints de autenticação.
 */
object RepositoryApiRoutes {

    /**
     * Configura as rotas REST.
     *
     * Se `AuthenticationService.BEAN` estiver populado, habilita
     * segurança (filtro JWT + endpoints de auth). Caso contrário,
     * registra apenas os controllers de entidade (modo teste/local).
     */
    fun configure(config: JavalinConfig) {
        val authService = AuthenticationService.BEAN.getOrNull()

        if (authService != null) {
            // Endpoints públicos de autenticação
            AuthApiController(authService).configure(config)

            // Filtro de segurança para endpoints protegidos
            val securityFilter = SecurityFilter(authService)
            config.routes.before("/api/repo/*", securityFilter::handle)
            config.routes.after("/api/repo/*") { SecurityContextHolder.clear() }
        }

        // Exception handler para AccessDeniedException
        config.routes.exception(AccessDeniedException::class.java) { e, ctx ->
            ctx.status(403)
            ctx.json(mapOf("error" to e.message))
        }

        // Controllers de entidades
        UserApiController.configure(config)
        ProductApiController.configure(config)
        PurchaseApiController.configure(config)
        PurchaseItemApiController.configure(config)
    }
}
