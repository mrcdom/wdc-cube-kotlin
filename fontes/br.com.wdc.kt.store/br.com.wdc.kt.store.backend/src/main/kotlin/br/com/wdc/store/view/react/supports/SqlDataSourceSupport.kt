package br.com.wdc.store.view.react.supports

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.util.Defer
import br.com.wdc.store.domain.config.AppConfig
import io.agroal.api.AgroalDataSource
import io.agroal.api.configuration.AgroalConnectionPoolConfiguration.ConnectionValidator
import io.agroal.api.configuration.AgroalConnectionPoolConfiguration.ExceptionSorter
import io.agroal.api.configuration.supplier.AgroalConnectionFactoryConfigurationSupplier
import io.agroal.api.configuration.supplier.AgroalConnectionPoolConfigurationSupplier
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier
import io.agroal.api.security.NamePrincipal
import io.agroal.api.security.SimplePassword
import java.nio.file.Path
import java.time.Duration
import javax.sql.DataSource
import org.jooq.SQLDialect

/**
 * Builds the backend's `DataSource`: an Agroal pool over H2 or PostgreSQL, according to the configured URL.
 *
 * Keys read, all under `database.`:
 *
 * | Key | Default |
 * |---|---|
 * | `url` | file-based H2, in the data directory |
 * | `username`, `password` | `sa` / `sa` |
 * | `schema` | — (PostgreSQL only: becomes each connection's `search_path`; created if missing) |
 * | `logSql` | `false` |
 * | `pool.maxSize` | 20 |
 * | `pool.minIdle` | 5 |
 * | `pool.connectionTimeoutSeconds` | 30 |
 * | `pool.maxLifetimeMinutes` | 30 (`0` turns it off) |
 *
 * The pool validates connections that sat idle and discards those that hit a fatal error: firewalls and the
 * database drop idle connections without notice, and without this the pool would hand out dead sockets.
 */
class SqlDataSourceSupport(private val config: AppConfig, private val dataDir: Path?) {

    val jdbcUrl: String = resolveJdbcUrl()

    /** The database, by URL: PostgreSQL or, by default, H2. */
    val dialect: SQLDialect = detectDialect(jdbcUrl)

    val logSql: Boolean get() = config.getBoolean("database.logSql", false)

    /**
     * Creates the pool.
     *
     * @param cleanUp receives the action that closes it
     */
    fun init(cleanUp: Defer): DataSource {
        val username = config.get("database.username", "sa")
        val password = config.get("database.password", "sa")
        val schema = config.get("database.schema")?.trim()?.takeIf { it.isNotEmpty() }
        val maxSize = config.getInt("database.pool.maxSize", 20)
        // the minimum cannot exceed the maximum
        val minIdle = config.getInt("database.pool.minIdle", 5).coerceIn(0, maxSize)
        val connectionTimeout = config.getInt("database.pool.connectionTimeoutSeconds", 30)
        val maxLifetimeMinutes = config.getInt("database.pool.maxLifetimeMinutes", 30)

        val factory = AgroalConnectionFactoryConfigurationSupplier()
            .connectionProviderClass(providerClass(dialect))
            .jdbcUrl(jdbcUrl)
            .principal(NamePrincipal(username))
            .credential(SimplePassword(password))

        if (dialect == SQLDialect.POSTGRES && schema != null) {
            require(SCHEMA_NAME.matches(schema)) { "invalid database.schema: '$schema'" }
            createSchemaIfMissing(username, password, schema)
            factory.initialSql("SET search_path TO $schema")
        }

        val pool = AgroalConnectionPoolConfigurationSupplier()
            .maxSize(maxSize)
            .minSize(minIdle)
            .initialSize(minIdle)
            .acquisitionTimeout(Duration.ofSeconds(connectionTimeout.toLong()))
            .connectionValidator(ConnectionValidator.defaultValidator())
            .idleValidationTimeout(Duration.ofSeconds(60))
            .reapTimeout(Duration.ofMinutes(10))
            .exceptionSorter(ExceptionSorter.defaultExceptionSorter())
            .connectionFactoryConfiguration(factory)
        if (maxLifetimeMinutes > 0) {
            pool.maxLifetime(Duration.ofMinutes(maxLifetimeMinutes.toLong()))
        }

        val dataSource = AgroalDataSource.from(AgroalDataSourceConfigurationSupplier().connectionPoolConfiguration(pool))
        cleanUp.push {
            try {
                dataSource.close()
                LOG.info("DataSource closed.")
            } catch (e: Exception) {
                LOG.warn("Failed to close the DataSource", e)
            }
        }

        LOG.info("Connection pool configured: dialect={}, maxSize={}, minIdle={}", dialect, maxSize, minIdle)
        LOG.info("jdbc configured with database {}", jdbcUrl)
        return dataSource
    }

    private fun resolveJdbcUrl(): String {
        val configured = config.get("database.url")
        if (!configured.isNullOrBlank()) {
            return configured.trim()
        }
        val dir = dataDir ?: throw IllegalStateException("No database.url and no data directory for the default database")
        return "jdbc:h2:file:${dir.resolve(DEFAULT_DB_NAME).toAbsolutePath()};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    }

    /** The schema must exist before the pool's first connection, which already arrives with the `search_path` on it. */
    private fun createSchemaIfMissing(username: String, password: String, schema: String) {
        java.sql.DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
            connection.createStatement().use { it.execute("CREATE SCHEMA IF NOT EXISTS $schema") }
        }
    }

    companion object {
        private val LOG = Log.getLogger("SqlDataSourceSupport")
        private const val DEFAULT_DB_NAME = "wedocode-store"
        private val SCHEMA_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

        fun detectDialect(jdbcUrl: String): SQLDialect =
            if (jdbcUrl.startsWith("jdbc:postgresql:") || jdbcUrl.startsWith("jdbc:pgsql:")) SQLDialect.POSTGRES else SQLDialect.H2

        private fun providerClass(dialect: SQLDialect): Class<*> = when (dialect) {
            SQLDialect.POSTGRES -> org.postgresql.ds.PGSimpleDataSource::class.java
            else -> org.h2.jdbcx.JdbcDataSource::class.java
        }
    }
}
