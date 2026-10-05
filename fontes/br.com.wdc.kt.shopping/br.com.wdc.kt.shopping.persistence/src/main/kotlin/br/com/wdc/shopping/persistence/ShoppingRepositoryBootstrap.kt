package br.com.wdc.shopping.persistence

import br.com.wdc.framework.commons.util.Defer
import br.com.wdc.framework.jooq.TransactionAwareConnectionProvider
import br.com.wdc.framework.persistence.transaction.TransactionServiceImpl
import br.com.wdc.shopping.domain.ShoppingTransactions
import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.user.UserRepository
import br.com.wdc.shopping.persistence.repository.product.ProductRepositoryImpl
import br.com.wdc.shopping.persistence.repository.purchase.PurchaseRepositoryImpl
import br.com.wdc.shopping.persistence.repository.purchaseitem.PurchaseItemRepositoryImpl
import br.com.wdc.shopping.persistence.repository.user.UserRepositoryImpl
import br.com.wdc.shopping.persistence.security.AuthenticationServiceImpl
import br.com.wdc.shopping.persistence.security.SecuredProductRepository
import br.com.wdc.shopping.persistence.security.SecuredPurchaseItemRepository
import br.com.wdc.shopping.persistence.security.SecuredPurchaseRepository
import br.com.wdc.shopping.persistence.security.SecuredUserRepository
import javax.sql.DataSource
import org.jooq.SQLDialect
import org.jooq.conf.RenderNameCase
import org.jooq.conf.Settings
import org.jooq.impl.DSL

/**
 * Bootstrap da persistência do Shopping sobre jOOQ: liga o módulo ao DataSource que o composition root lhe
 * entrega (backend, testes).
 *
 * [initialize] registra os quatro repositórios; [initializeSecurity], chamado depois e só quando há segredo
 * JWT configurado, decora-os com o controle de acesso e registra o serviço de autenticação.
 */
object ShoppingRepositoryBootstrap {

    /**
     * @param dataSource DataSource **deste módulo** — usado pelo `DSLContext` e pelo `TransactionService`
     * @param logSql     se `true`, o jOOQ registra os SQLs executados (nível DEBUG)
     * @param dialect    banco em uso ([SQLDialect.H2] ou [SQLDialect.POSTGRES])
     * @param cleanUp    recebe a ação que desfaz o que foi registrado aqui
     */
    fun initialize(dataSource: DataSource, logSql: Boolean = false, dialect: SQLDialect = SQLDialect.H2, cleanUp: Defer) {
        var settings = Settings().withExecuteLogging(logSql)
        if (dialect == SQLDialect.POSTGRES) {
            // As classes geradas vêm do H2: esquema "PUBLIC" e nomes em maiúsculas. No PostgreSQL os nomes do DDL
            // são minúsculos e o esquema se resolve pelo search_path.
            settings = settings.withRenderSchema(false).withRenderNameCase(RenderNameCase.LOWER)
        }

        // Dentro de uma transação as consultas usam a conexão do escopo; fora dela, uma avulsa em autocommit.
        ShoppingDSLContext.BEAN.set(DSL.using(TransactionAwareConnectionProvider(dataSource), dialect, settings))
        ShoppingTransactions.BEAN.set(TransactionServiceImpl { dataSource })
        UserRepository.BEAN.set(UserRepositoryImpl())
        ProductRepository.BEAN.set(ProductRepositoryImpl())
        PurchaseRepository.BEAN.set(PurchaseRepositoryImpl())
        PurchaseItemRepository.BEAN.set(PurchaseItemRepositoryImpl())

        cleanUp.push {
            UserRepository.BEAN.set(null)
            ProductRepository.BEAN.set(null)
            PurchaseRepository.BEAN.set(null)
            PurchaseItemRepository.BEAN.set(null)
            ShoppingTransactions.BEAN.set(null)
            ShoppingDSLContext.BEAN.set(null)
        }
    }

    /**
     * Liga a segurança: decora os repositórios já registrados e registra o [AuthenticationService].
     *
     * @param cleanUp recebe a ação que retira o serviço de autenticação (os repositórios saem com [initialize])
     */
    fun initializeSecurity(jwtSecret: String, refreshTokenTtlDays: Int = 7, cleanUp: Defer) {
        val rawUserRepo = UserRepository.BEAN.get()
        val rawPurchaseRepo = PurchaseRepository.BEAN.get()

        UserRepository.BEAN.set(SecuredUserRepository(rawUserRepo))
        ProductRepository.BEAN.set(SecuredProductRepository(ProductRepository.BEAN.get()))
        PurchaseRepository.BEAN.set(SecuredPurchaseRepository(rawPurchaseRepo))
        PurchaseItemRepository.BEAN.set(SecuredPurchaseItemRepository(PurchaseItemRepository.BEAN.get(), rawPurchaseRepo))

        AuthenticationService.BEAN.set(AuthenticationServiceImpl(rawUserRepo, jwtSecret, refreshTokenTtlDays))
        cleanUp.push { AuthenticationService.BEAN.set(null) }
    }
}
