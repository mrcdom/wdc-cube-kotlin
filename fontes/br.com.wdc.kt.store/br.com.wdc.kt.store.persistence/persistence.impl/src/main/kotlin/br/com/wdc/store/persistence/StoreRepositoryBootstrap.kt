package br.com.wdc.store.persistence

import br.com.wdc.framework.commons.util.Defer
import br.com.wdc.framework.jooq.TransactionAwareConnectionProvider
import br.com.wdc.framework.persistence.transaction.TransactionServiceImpl
import br.com.wdc.store.domain.StoreTransactions
import br.com.wdc.store.domain.product.ProductRepository
import br.com.wdc.store.domain.purchase.PurchaseRepository
import br.com.wdc.store.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.store.domain.security.AuthenticationService
import br.com.wdc.store.domain.user.UserRepository
import br.com.wdc.store.persistence.repository.product.ProductRepositoryImpl
import br.com.wdc.store.persistence.repository.purchase.PurchaseRepositoryImpl
import br.com.wdc.store.persistence.repository.purchaseitem.PurchaseItemRepositoryImpl
import br.com.wdc.store.persistence.repository.user.UserRepositoryImpl
import br.com.wdc.store.persistence.security.AuthenticationServiceImpl
import javax.sql.DataSource
import org.jooq.SQLDialect
import org.jooq.conf.RenderNameCase
import org.jooq.conf.Settings
import org.jooq.impl.DSL

/**
 * Bootstrap of the Store persistence over jOOQ: binds the module to the DataSource the composition root
 * hands it (backend, tests).
 *
 * [initialize] registers the four repositories; [initializeSecurity], called afterwards and only when a JWT
 * secret is configured, registers the authentication service.
 */
object StoreRepositoryBootstrap {

    /**
     * @param dataSource DataSource **of this module** — used by the `DSLContext` and the `TransactionService`
     * @param logSql     if `true`, jOOQ logs the executed SQL (DEBUG level)
     * @param dialect    database in use ([SQLDialect.H2] or [SQLDialect.POSTGRES])
     * @param cleanUp    receives the action that undoes what was registered here
     */
    fun initialize(dataSource: DataSource, logSql: Boolean = false, dialect: SQLDialect = SQLDialect.H2, cleanUp: Defer) {
        var settings = Settings().withExecuteLogging(logSql)
        if (dialect == SQLDialect.POSTGRES) {
            // The generated classes come from H2: schema "PUBLIC" and upper-case names. On PostgreSQL the DDL
            // names are lower-case and the schema is resolved by the search_path.
            settings = settings.withRenderSchema(false).withRenderNameCase(RenderNameCase.LOWER)
        }

        // Inside a transaction the queries use the scope's connection; outside one, a standalone one in autocommit.
        StoreDSLContext.BEAN.set(DSL.using(TransactionAwareConnectionProvider(dataSource), dialect, settings))
        StoreTransactions.BEAN.set(TransactionServiceImpl { dataSource })
        UserRepository.BEAN.set(UserRepositoryImpl())
        ProductRepository.BEAN.set(ProductRepositoryImpl())
        PurchaseRepository.BEAN.set(PurchaseRepositoryImpl())
        PurchaseItemRepository.BEAN.set(PurchaseItemRepositoryImpl())

        cleanUp.push {
            UserRepository.BEAN.set(null)
            ProductRepository.BEAN.set(null)
            PurchaseRepository.BEAN.set(null)
            PurchaseItemRepository.BEAN.set(null)
            StoreTransactions.BEAN.set(null)
            StoreDSLContext.BEAN.set(null)
        }
    }

    /**
     * Turns authentication on: registers the [AuthenticationService], after which the REST API requires and
     * checks access. The repositories do not change — access control belongs to the HTTP boundary.
     *
     * @param cleanUp receives the action that removes the authentication service
     */
    fun initializeSecurity(jwtSecret: String, refreshTokenTtlDays: Int = 7, cleanUp: Defer) {
        AuthenticationService.BEAN.set(AuthenticationServiceImpl(UserRepository.BEAN.get(), jwtSecret, refreshTokenTtlDays))
        cleanUp.push { AuthenticationService.BEAN.set(null) }
    }
}
