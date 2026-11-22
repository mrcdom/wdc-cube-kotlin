package br.com.wdc.shopping.test.util

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import org.h2.jdbcx.JdbcConnectionPool
import org.jooq.SQLDialect
import org.postgresql.ds.PGSimpleDataSource

/**
 * The database of a test environment.
 *
 * By default, in-memory H2. With `-Dshopping.test.db=postgres` (the `testPostgres` task), the same suite runs on
 * PostgreSQL:
 *
 * - **embedded** (`io.zonky.test:embedded-postgres`), which starts on its own — one instance per JVM and a new
 *   database per environment;
 * - or the server given by `SHOPPING_TEST_PG_URL` (with `SHOPPING_TEST_PG_USER` and
 *   `SHOPPING_TEST_PG_PASSWORD`), when you want to check against a real PostgreSQL. **The test seed
 *   deletes and recreates the data of that database.**
 */
class TestDatabase private constructor(
    val dataSource: DataSource,
    val dialect: SQLDialect,
    private val onClose: () -> Unit,
) {

    fun close() = onClose()

    companion object {
        /** `true` when the suite is running on PostgreSQL. */
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

            // one database per environment, so that a test does not see another one's data
            val database = "t${sequence.incrementAndGet()}_" + name.lowercase().replace(Regex("[^a-z0-9]"), "_").take(40)
            embedded.postgresDatabase.connection.use { c ->
                c.createStatement().use { it.execute("CREATE DATABASE $database") }
            }
            return TestDatabase(embedded.getDatabase("postgres", database), SQLDialect.POSTGRES) {}
        }
    }
}
