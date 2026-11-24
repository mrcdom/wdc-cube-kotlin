package br.com.wdc.shopping.view.react

import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.shopping.domain.ShoppingConfig
import br.com.wdc.shopping.domain.config.AppConfig
import br.com.wdc.shopping.domain.security.CryptoProvider
import br.com.wdc.shopping.domain.security.JceCryptoProvider
import br.com.wdc.framework.commons.util.Defer
import br.com.wdc.framework.persistence.transaction.RemoteTransactionCoordinatorImpl
import br.com.wdc.framework.persistence.transaction.RemoteTransactionOptions
import br.com.wdc.shopping.persistence.ShoppingRepositoryBootstrap
import br.com.wdc.shopping.persistence.rest.RemoteTransactions
import br.com.wdc.shopping.persistence.concurrent.ScheduledExecutorAdapter
import br.com.wdc.shopping.scripts.sgbd.DBCreate
import br.com.wdc.shopping.view.react.supports.SqlDataSourceSupport
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import br.com.wdc.shopping.persistence.security.AuthenticationServiceImpl
import br.com.wdc.shopping.domain.security.AuthenticationService

/**
 * @param config the service configuration, already loaded from the work directory
 */
class BusinessContext(private val config: AppConfig) {

    companion object {
        private val LOG = Log.getLogger("BusinessContext")
    }

    private val cleanUp = Defer()

    fun stop() {
        cleanUp.run()
        ScheduledExecutor.BEAN.set(null)
        CryptoProvider.BEAN.set(null)
    }

    fun start() {
        try {
            CryptoProvider.BEAN.set(JceCryptoProvider())

            val scheduledExecutor = createScheduledExecutor()
            ScheduledExecutor.BEAN.set(ScheduledExecutorAdapter(scheduledExecutor))

            val dataSourceSupport = SqlDataSourceSupport(config, ShoppingConfig.dataDir)
            val dataSource = dataSourceSupport.init(cleanUp)

            dataSource.connection.use { connection ->
                val command = DBCreate().withConnection(connection)
                if (config.getBoolean("database.reset", false)) {
                    command.withReset()
                }
                command.run()
            }

            ShoppingRepositoryBootstrap.initialize(dataSource, dataSourceSupport.logSql, dataSourceSupport.dialect, cleanUp)

            // the transactions the REST clients open and close on their own
            val coordinator = RemoteTransactionCoordinatorImpl({ dataSource }, RemoteTransactionOptions.fromConfig("", config::getInt))
            RemoteTransactions.COORDINATOR.set(coordinator)
            cleanUp.push { RemoteTransactions.COORDINATOR.set(null) }
            // rolls back abandoned transactions even when no one opens a new one
            val reaper = coordinator.startReaper(scheduledExecutor)
            cleanUp.push { reaper.close() }

            val jwtSecret = ShoppingConfig.jwtSecret
            if (!jwtSecret.isNullOrBlank()) {
                ShoppingRepositoryBootstrap.initializeSecurity(jwtSecret, ShoppingConfig.refreshTokenTtlDays, cleanUp)
                // drops the expired sessions and the unanswered login challenges, even when no one signs in
                (AuthenticationService.BEAN.getOrNull() as? AuthenticationServiceImpl)?.let { auth ->
                    val cleanup = auth.startCleanup(scheduledExecutor)
                    cleanUp.push { cleanup.close() }
                }
            }

            LOG.info("Storefront backend context initialized with database {}", dataSourceSupport.jdbcUrl)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to initialize shopping backend context", e)
        }
    }

    private fun createScheduledExecutor(): ScheduledExecutorService {
        return Executors.newScheduledThreadPool(1, VirtualThreadFactory.ofVirtual("ScheduledTasks"))
    }
}
