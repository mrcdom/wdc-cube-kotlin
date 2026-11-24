package br.com.wdc.store.persistence.rest

import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.security.BearerAuthenticator
import br.com.wdc.framework.rest.transaction.RemoteTransactionRoutes
import br.com.wdc.store.domain.StoreConfig
import br.com.wdc.store.domain.StoreTransactions
import br.com.wdc.store.domain.security.AuthenticationService
import br.com.wdc.store.persistence.rest.doc.RepositoryApiDocs
import io.javalin.config.JavalinConfig

/**
 * The repositories' REST API, built on `framework-rest`.
 *
 * If the [AuthenticationService] is initialized (via `StoreRepositoryBootstrap.initializeSecurity`), the API
 * starts secured: `Authorization: Bearer` on everything, except the `/api/auth` routes, reading a product image
 * and `/openapi.json`. What each role may do with each entity is decided by its data policy (`security.XxxDataPolicy`),
 * set on the entity's RPC.
 * Without the service, the API is open and `/api/auth` does not exist.
 *
 * Transactions come from the holders on each request: [StoreTransactions] (one per write request) and
 * [RemoteTransactions] (the ones the client opens through `/api/tx`).
 */
object RepositoryApiRoutes {

    /**
     * Builds the API from what is registered in the holders now (the authentication service decides security).
     * [authRequestsPerMinute] limits `/api/auth` per client address (`0` = no limit).
     */
    fun create(authRequestsPerMinute: Int = StoreConfig.authRequestsPerMinute): RestApi {
        val authService = AuthenticationService.BEAN.getOrNull()
        return RestApi {
            if (authService != null) {
                security {
                    authenticator = BearerAuthenticator { token -> authService.resolveToken(token)?.let(::StorePrincipal) }
                    permitAll("/api/auth/**", "GET /api/repo/product/{id}/image", "GET /openapi.json")
                }
            }
            transactions {
                serviceSource = { StoreTransactions.BEAN.getOrNull() }
                coordinatorSource = { RemoteTransactions.COORDINATOR.getOrNull() }
            }
            openApi { RepositoryApiDocs.configure(this) }

            if (authService != null) {
                AuthApiController.register(this, authService, authRequestsPerMinute)
            }
            UserApiController.register(this)
            ProductApiController.register(this)
            PurchaseApiController.register(this)
            PurchaseItemApiController.register(this)
            RemoteTransactionRoutes.register(this, transactions, doc = RepositoryApiDocs::transactionDoc)
        }
    }

    /** Registers the API on the Javalin being configured. Used by the backend and the tests. */
    fun configure(config: JavalinConfig): RestApi = create().also { it.install(config) }
}
