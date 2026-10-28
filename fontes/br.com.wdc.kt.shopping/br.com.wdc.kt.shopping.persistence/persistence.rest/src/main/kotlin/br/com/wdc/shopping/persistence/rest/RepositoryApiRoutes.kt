package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.security.BearerAuthenticator
import br.com.wdc.framework.rest.transaction.RemoteTransactionRoutes
import br.com.wdc.shopping.domain.ShoppingTransactions
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.persistence.rest.doc.RepositoryApiDocs
import io.javalin.config.JavalinConfig

/**
 * A API REST dos repositórios, montada sobre o `framework-rest`.
 *
 * Se o [AuthenticationService] estiver inicializado (via `ShoppingRepositoryBootstrap.initializeSecurity`), a API
 * sobe com segurança: `Authorization: Bearer` em tudo, salvo as rotas de `/api/auth`, a leitura da imagem de
 * produto e o `/openapi.json`. A permissão e o alcance de cada operação são conferidos pelas rotas
 * ([ApiSecurity]). Sem o serviço, a API é aberta e `/api/auth` não existe.
 *
 * As transações vêm dos holders a cada requisição: [ShoppingTransactions] (uma por requisição de escrita) e
 * [RemoteTransactions] (as que o cliente abre por `/api/tx`).
 */
object RepositoryApiRoutes {

    /** Monta a API com o que estiver registrado nos holders agora (o serviço de autenticação decide a segurança). */
    fun create(): RestApi {
        val authService = AuthenticationService.BEAN.getOrNull()
        return RestApi {
            if (authService != null) {
                security {
                    authenticator = BearerAuthenticator { token -> authService.resolveToken(token)?.let(::ShoppingPrincipal) }
                    permitAll("/api/auth/**", "GET /api/repo/product/{id}/image", "GET /openapi.json")
                }
            }
            transactions {
                serviceSource = { ShoppingTransactions.BEAN.getOrNull() }
                coordinatorSource = { RemoteTransactions.COORDINATOR.getOrNull() }
            }
            openApi { RepositoryApiDocs.configure(this) }

            if (authService != null) {
                AuthApiController.register(this, authService)
            }
            UserApiController.register(this)
            ProductApiController.register(this)
            PurchaseApiController.register(this)
            PurchaseItemApiController.register(this)
            RemoteTransactionRoutes.register(this, transactions, doc = RepositoryApiDocs::transactionDoc)
        }
    }

    /** Registra a API no Javalin em configuração. É usado pelo backend e pelos testes. */
    fun configure(config: JavalinConfig): RestApi = create().also { it.install(config) }
}
