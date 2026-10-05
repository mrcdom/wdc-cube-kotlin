package br.com.wdc.shopping.persistence

import br.com.wdc.framework.commons.util.Defer
import br.com.wdc.framework.jooq.TransactionAwareConnectionProvider
import br.com.wdc.framework.persistence.transaction.TransactionServiceImpl
import br.com.wdc.shopping.domain.ShoppingTransactions
import javax.sql.DataSource
import org.jooq.SQLDialect
import org.jooq.conf.RenderNameCase
import org.jooq.conf.Settings
import org.jooq.impl.DSL

/**
 * Bootstrap da persistência do Shopping sobre jOOQ: liga o módulo ao DataSource que o composition root lhe
 * entrega (backend, testes).
 *
 * Em migração: os repositórios ainda são registrados por [RepositoryBootstrap]; aqui ficam o [ShoppingDSLContext]
 * e o serviço de transação do módulo, e os repositórios passam para cá à medida que cada entidade é portada.
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

        cleanUp.push {
            ShoppingTransactions.BEAN.set(null)
            ShoppingDSLContext.BEAN.set(null)
        }
    }
}
