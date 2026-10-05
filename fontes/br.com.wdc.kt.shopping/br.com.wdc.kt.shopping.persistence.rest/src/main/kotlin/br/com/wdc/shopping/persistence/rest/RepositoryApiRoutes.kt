package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.exception.TransactionConflictException
import br.com.wdc.framework.domain.exception.TransactionLimitExceededException
import br.com.wdc.shopping.domain.exception.AccessDeniedException
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.security.SecurityContextHolder
import io.javalin.config.JavalinConfig

/**
 * Registra todos os endpoints REST da API de repositório no Javalin.
 *
 * Se o [AuthenticationService] estiver inicializado (via
 * `ShoppingRepositoryBootstrap.initializeSecurity`), registra automaticamente
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
            // a transação remota é de quem a abriu: os seus endpoints exigem a mesma autenticação
            config.routes.before("/api/tx/*", securityFilter::handle)
            config.routes.after("/api/tx/*") { SecurityContextHolder.clear() }
        }

        // Exception handler para AccessDeniedException
        config.routes.exception(AccessDeniedException::class.java) { e, ctx ->
            ctx.status(403)
            ctx.json(mapOf("error" to e.message))
        }

        // Pedido recusado por validação (ordenação desconhecida, delete sem filtro…) → 400, com o motivo no corpo
        config.routes.exception(InvalidRequestException::class.java) { e, ctx ->
            ctx.status(400)
            ctx.json(mapOf("error" to e.message))
        }

        // Transação remota: uso indevido (dono errado → 403 acima; desfecho oposto, uso concorrente, escrita sem o
        // cabeçalho → 409) e tetos de transações abertas → 429
        config.routes.exception(TransactionConflictException::class.java) { e, ctx ->
            ctx.status(409)
            ctx.json(mapOf("error" to e.message))
        }
        config.routes.exception(TransactionLimitExceededException::class.java) { e, ctx ->
            ctx.status(429)
            ctx.json(mapOf("error" to e.message))
        }

        // Controllers de entidades
        UserApiController.configure(config)
        ProductApiController.configure(config)
        PurchaseApiController.configure(config)
        PurchaseItemApiController.configure(config)
        TxApiController.configure(config)
    }
}
