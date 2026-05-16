package br.com.wdc.shopping.view.react

import br.com.wdc.framework.commons.concurrent.ScheduledExecutor
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.sql.SqlDataSource
import br.com.wdc.framework.commons.sql.SqlDataSourceDelegate
import br.com.wdc.shopping.domain.ShoppingConfig
import br.com.wdc.shopping.domain.config.AppConfig
import br.com.wdc.shopping.domain.security.CryptoProvider
import br.com.wdc.shopping.domain.security.JceCryptoProvider
import br.com.wdc.shopping.persistence.RepositoryBootstrap
import br.com.wdc.shopping.persistence.concurrent.ScheduledExecutorAdapter
import br.com.wdc.shopping.scripts.sgbd.DBCreate
import org.h2.jdbcx.JdbcDataSource
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class BusinessContext {

    companion object {
        private val LOG = Log.getLogger("BusinessContext")
        private const val DEFAULT_DB_NAME = "wedocode-shopping"
    }

    fun stop() {
        RepositoryBootstrap.release()
        ScheduledExecutor.BEAN.set(null)
        SqlDataSource.BEAN.set(null)
        CryptoProvider.BEAN.set(null)
    }

    fun start() {
        try {
            val config = AppConfig.load()
            ShoppingConfig.Internals.configure(config)

            CryptoProvider.BEAN.set(JceCryptoProvider())

            val scheduledExecutor = createScheduledExecutor()
            ScheduledExecutor.BEAN.set(ScheduledExecutorAdapter(scheduledExecutor))

            val dataSource = JdbcDataSource()
            dataSource.setURL(resolveJdbcUrl(config, ShoppingConfig.dataDir!!))
            dataSource.user = config.get("database.username", "sa")
            dataSource.password = config.get("database.password", "sa")

            SqlDataSource.BEAN.set(SqlDataSourceDelegate(dataSource))

            dataSource.connection.use { connection ->
                val command = DBCreate().withConnection(connection)
                if (config.getBoolean("database.reset", false)) {
                    command.withReset()
                }
                command.run()
            }

            RepositoryBootstrap.initialize()

            val jwtSecret = ShoppingConfig.jwtSecret
            if (!jwtSecret.isNullOrBlank()) {
                RepositoryBootstrap.initializeSecurity(jwtSecret, ShoppingConfig.refreshTokenTtlDays)
            }

            LOG.info("Shopping backend context initialized with database {}", dataSource.getURL())
        } catch (e: Exception) {
            throw IllegalStateException("Failed to initialize shopping backend context", e)
        }
    }

    private fun resolveJdbcUrl(config: AppConfig, dataDir: Path): String {
        val configuredUrl = config.get("database.url")
        if (!configuredUrl.isNullOrBlank()) {
            return configuredUrl
        }
        return "jdbc:h2:file:${dataDir.resolve(DEFAULT_DB_NAME).toAbsolutePath()};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    }

    private fun createScheduledExecutor(): ScheduledExecutorService {
        return Executors.newScheduledThreadPool(1, VirtualThreadFactory.ofVirtual("ScheduledTasks"))
    }
}
