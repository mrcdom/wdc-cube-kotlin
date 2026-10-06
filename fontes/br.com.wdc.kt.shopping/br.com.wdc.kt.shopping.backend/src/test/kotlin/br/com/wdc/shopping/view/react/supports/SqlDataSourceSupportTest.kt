package br.com.wdc.shopping.view.react.supports

import br.com.wdc.framework.commons.util.Defer
import br.com.wdc.shopping.domain.config.AppConfig
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.nio.file.Path
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import org.jooq.SQLDialect

class SqlDataSourceSupportTest {

    private fun config(vararg entries: Pair<String, String>): AppConfig =
        entries.fold(AppConfig.empty()) { config, (key, value) -> config.withOverride(key, value) }

    private fun h2(name: String, vararg entries: Pair<String, String>) =
        SqlDataSourceSupport(config("database.url" to "jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1", *entries), null)

    @Test
    fun dialect_comesFromTheUrl() {
        assertEquals(SQLDialect.H2, SqlDataSourceSupport.detectDialect("jdbc:h2:mem:x"))
        assertEquals(SQLDialect.POSTGRES, SqlDataSourceSupport.detectDialect("jdbc:postgresql://localhost/x"))
        assertEquals(SQLDialect.POSTGRES, SqlDataSourceSupport.detectDialect("jdbc:pgsql://localhost/x"))
    }

    @Test
    fun withoutUrl_theDatabaseIsAnH2FileInTheDataDirectory() {
        val support = SqlDataSourceSupport(config(), Path.of("/tmp/shopping-data"))
        assertEquals(SQLDialect.H2, support.dialect)
        assertTrue(support.jdbcUrl.startsWith("jdbc:h2:file:/tmp/shopping-data/wedocode-shopping;"), support.jdbcUrl)
        assertFalse(support.logSql)
        assertFailsWith<IllegalStateException> { SqlDataSourceSupport(config(), null) }
    }

    @Test
    fun h2Pool_handsOutConnections_andClosesWithTheCleanUp() {
        val cleanUp = Defer()
        val support = h2("pool-basic", "database.logSql" to "true")
        val dataSource = support.init(cleanUp)
        assertTrue(support.logSql)

        dataSource.connection.use { first ->
            dataSource.connection.use { second ->
                assertNotSame(first, second)
                second.createStatement().use { it.execute("CREATE TABLE T (ID INT)") }
            }
            first.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COUNT(*) FROM T").use { rs -> assertTrue(rs.next()) }
            }
        }

        cleanUp.run()
        assertFailsWith<SQLException> { dataSource.connection }
    }

    @Test
    fun pool_isBounded_andGivesUpAfterTheConfiguredTimeout() {
        val cleanUp = Defer()
        try {
            val dataSource = h2(
                "pool-bounded",
                "database.pool.maxSize" to "1",
                "database.pool.minIdle" to "5", // maior que o máximo: é contido
                "database.pool.connectionTimeoutSeconds" to "1",
            ).init(cleanUp)

            dataSource.connection.use {
                val started = System.nanoTime()
                assertFailsWith<SQLException> { dataSource.connection }
                val waitedMs = (System.nanoTime() - started) / 1_000_000
                assertTrue(waitedMs in 800..5_000, "esperou $waitedMs ms")
            }
            // devolvida, a conexão volta a servir
            dataSource.connection.use { assertTrue(it.isValid(1)) }
        } finally {
            cleanUp.run()
        }
    }

    @Test
    fun postgres_usesTheConfiguredSchema_creatingItWhenMissing() {
        EmbeddedPostgres.builder().start().use { pg ->
            val cleanUp = Defer()
            try {
                val support = SqlDataSourceSupport(
                    config(
                        "database.url" to pg.getJdbcUrl("postgres", "postgres"),
                        "database.username" to "postgres",
                        "database.password" to "postgres",
                        "database.schema" to "shopping_test",
                    ),
                    null,
                )
                assertEquals(SQLDialect.POSTGRES, support.dialect)
                val dataSource = support.init(cleanUp)

                dataSource.connection.use { c ->
                    assertEquals("shopping_test", c.schema)
                    c.createStatement().use { it.execute("CREATE TABLE en_probe (id INT)") }
                }
                // outra conexão do pool chega no mesmo esquema
                dataSource.connection.use { c ->
                    c.createStatement().use { stmt ->
                        stmt.executeQuery("SELECT table_schema FROM information_schema.tables WHERE table_name = 'en_probe'").use { rs ->
                            assertTrue(rs.next())
                            assertEquals("shopping_test", rs.getString(1))
                        }
                    }
                }
            } finally {
                cleanUp.run()
            }
        }
    }

    @Test
    fun schemaName_isValidated() {
        val support = SqlDataSourceSupport(
            config("database.url" to "jdbc:postgresql://localhost:1/x", "database.schema" to "a; DROP SCHEMA public"),
            null,
        )
        assertFailsWith<IllegalArgumentException> { support.init(Defer()) }
    }
}
