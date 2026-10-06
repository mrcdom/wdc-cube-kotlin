package br.com.wdc.shopping.view.react.supports

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.util.Defer
import br.com.wdc.shopping.domain.config.AppConfig
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
 * Monta o `DataSource` do backend: um pool Agroal sobre H2 ou PostgreSQL, conforme a URL configurada.
 *
 * Chaves lidas, todas sob `database.`:
 *
 * | Chave | Padrão |
 * |---|---|
 * | `url` | H2 em arquivo, no diretório de dados |
 * | `username`, `password` | `sa` / `sa` |
 * | `schema` | — (só PostgreSQL: vira o `search_path` de cada conexão; é criado se não existir) |
 * | `logSql` | `false` |
 * | `pool.maxSize` | 20 |
 * | `pool.minIdle` | 5 |
 * | `pool.connectionTimeoutSeconds` | 30 |
 * | `pool.maxLifetimeMinutes` | 30 (`0` desliga) |
 *
 * O pool valida as conexões que ficaram ociosas e descarta as que deram erro fatal: firewall e banco derrubam
 * conexões paradas sem avisar, e sem isso o pool devolveria sockets mortos.
 */
class SqlDataSourceSupport(private val config: AppConfig, private val dataDir: Path?) {

    val jdbcUrl: String = resolveJdbcUrl()

    /** O banco, pela URL: PostgreSQL ou, por padrão, H2. */
    val dialect: SQLDialect = detectDialect(jdbcUrl)

    val logSql: Boolean get() = config.getBoolean("database.logSql", false)

    /**
     * Cria o pool.
     *
     * @param cleanUp recebe a ação que o encerra
     */
    fun init(cleanUp: Defer): DataSource {
        val username = config.get("database.username", "sa")
        val password = config.get("database.password", "sa")
        val schema = config.get("database.schema")?.trim()?.takeIf { it.isNotEmpty() }
        val maxSize = config.getInt("database.pool.maxSize", 20)
        // o mínimo não pode passar do máximo
        val minIdle = config.getInt("database.pool.minIdle", 5).coerceIn(0, maxSize)
        val connectionTimeout = config.getInt("database.pool.connectionTimeoutSeconds", 30)
        val maxLifetimeMinutes = config.getInt("database.pool.maxLifetimeMinutes", 30)

        val factory = AgroalConnectionFactoryConfigurationSupplier()
            .connectionProviderClass(providerClass(dialect))
            .jdbcUrl(jdbcUrl)
            .principal(NamePrincipal(username))
            .credential(SimplePassword(password))

        if (dialect == SQLDialect.POSTGRES && schema != null) {
            require(SCHEMA_NAME.matches(schema)) { "database.schema inválido: '$schema'" }
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
                LOG.info("DataSource encerrado.")
            } catch (e: Exception) {
                LOG.warn("Falha ao encerrar o DataSource", e)
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
        val dir = dataDir ?: throw IllegalStateException("Sem database.url e sem diretório de dados para o banco padrão")
        return "jdbc:h2:file:${dir.resolve(DEFAULT_DB_NAME).toAbsolutePath()};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    }

    /** O esquema precisa existir antes da primeira conexão do pool, que já chega com o `search_path` nele. */
    private fun createSchemaIfMissing(username: String, password: String, schema: String) {
        java.sql.DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
            connection.createStatement().use { it.execute("CREATE SCHEMA IF NOT EXISTS $schema") }
        }
    }

    companion object {
        private val LOG = Log.getLogger("SqlDataSourceSupport")
        private const val DEFAULT_DB_NAME = "wedocode-shopping"
        private val SCHEMA_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

        fun detectDialect(jdbcUrl: String): SQLDialect =
            if (jdbcUrl.startsWith("jdbc:postgresql:") || jdbcUrl.startsWith("jdbc:pgsql:")) SQLDialect.POSTGRES else SQLDialect.H2

        private fun providerClass(dialect: SQLDialect): Class<*> = when (dialect) {
            SQLDialect.POSTGRES -> org.postgresql.ds.PGSimpleDataSource::class.java
            else -> org.h2.jdbcx.JdbcDataSource::class.java
        }
    }
}
