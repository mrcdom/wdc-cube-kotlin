package br.com.wdc.shopping.test.util

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import org.h2.jdbcx.JdbcConnectionPool
import org.jooq.SQLDialect
import org.postgresql.ds.PGSimpleDataSource

/**
 * O banco de um ambiente de teste.
 *
 * Por padrão, H2 em memória. Com `-Dshopping.test.db=postgres` (a task `testPostgres`), a mesma suíte roda em
 * PostgreSQL:
 *
 * - **embutido** (`io.zonky.test:embedded-postgres`), que sobe sozinho — uma instância por JVM e uma base nova
 *   por ambiente;
 * - ou o servidor indicado por `SHOPPING_TEST_PG_URL` (com `SHOPPING_TEST_PG_USER` e
 *   `SHOPPING_TEST_PG_PASSWORD`), quando se quer conferir contra um PostgreSQL de verdade. **A carga de teste
 *   apaga e recria os dados dessa base.**
 */
class TestDatabase private constructor(
    val dataSource: DataSource,
    val dialect: SQLDialect,
    private val onClose: () -> Unit,
) {

    fun close() = onClose()

    companion object {
        /** `true` quando a suíte está rodando em PostgreSQL. */
        val postgres: Boolean = System.getProperty("shopping.test.db").equals("postgres", ignoreCase = true)

        private val sequence = AtomicInteger()

        private val embedded: EmbeddedPostgres by lazy {
            EmbeddedPostgres.builder().start().also { pg -> Runtime.getRuntime().addShutdownHook(Thread { pg.close() }) }
        }

        fun open(name: String): TestDatabase = if (postgres) openPostgres(name) else openH2(name)

        private fun openH2(name: String): TestDatabase {
            val pool = JdbcConnectionPool.create("jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "sa", "sa")
            pool.maxConnections = 10
            return TestDatabase(pool, SQLDialect.H2) { pool.dispose() }
        }

        private fun openPostgres(name: String): TestDatabase {
            val externalUrl = System.getenv("SHOPPING_TEST_PG_URL")
            if (!externalUrl.isNullOrBlank()) {
                val ds = PGSimpleDataSource().apply {
                    setURL(externalUrl)
                    user = System.getenv("SHOPPING_TEST_PG_USER") ?: "postgres"
                    password = System.getenv("SHOPPING_TEST_PG_PASSWORD") ?: ""
                }
                return TestDatabase(ds, SQLDialect.POSTGRES) {}
            }

            // uma base por ambiente, para um teste não ver os dados de outro
            val database = "t${sequence.incrementAndGet()}_" + name.lowercase().replace(Regex("[^a-z0-9]"), "_").take(40)
            embedded.postgresDatabase.connection.use { c ->
                c.createStatement().use { it.execute("CREATE DATABASE $database") }
            }
            return TestDatabase(embedded.getDatabase("postgres", database), SQLDialect.POSTGRES) {}
        }
    }
}
