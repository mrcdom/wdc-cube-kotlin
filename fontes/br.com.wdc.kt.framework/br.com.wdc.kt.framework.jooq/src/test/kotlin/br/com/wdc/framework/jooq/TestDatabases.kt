package br.com.wdc.framework.jooq

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import org.h2.jdbcx.JdbcDataSource
import org.jooq.SQLDialect
import org.jooq.conf.RenderNameCase
import org.jooq.conf.Settings

/** Um banco de teste: o DataSource, o dialeto e como criar o esquema de exemplo nele. */
class TestDatabase(val dialect: SQLDialect, val dataSource: DataSource, val settings: Settings, private val binaryType: String) {

    val ddl: List<String>
        get() = listOf(
            "DROP TABLE IF EXISTS NOTE",
            "DROP TABLE IF EXISTS BOOK",
            "DROP TABLE IF EXISTS AUTHOR",
            "CREATE TABLE AUTHOR (ID BIGINT PRIMARY KEY, NAME VARCHAR(100))",
            """CREATE TABLE BOOK (ID BIGINT PRIMARY KEY, AUTHOR_ID BIGINT, TITLE VARCHAR(200), PRICE NUMERIC(20,2),
               PAGES INT, ACTIVE BOOLEAN, PUBLISHED TIMESTAMP, COVER $binaryType, KIND VARCHAR(20))""",
            "CREATE TABLE NOTE (AUTHOR_ID BIGINT, TEXT VARCHAR(200))",
        )

    companion object {
        private val h2Counter = AtomicInteger()

        /** H2 em memória, um banco novo a cada chamada. */
        fun h2(): TestDatabase {
            val ds = JdbcDataSource().apply {
                setURL("jdbc:h2:mem:jooq-${h2Counter.incrementAndGet()};DB_CLOSE_DELAY=-1")
                user = "sa"
                password = "sa"
            }
            return TestDatabase(SQLDialect.H2, ds, Settings(), "VARBINARY(100000)")
        }

        /** PostgreSQL embutido, iniciado uma vez por JVM e encerrado no fim dela. */
        private val embeddedPostgres: EmbeddedPostgres by lazy {
            EmbeddedPostgres.builder().start().also { pg -> Runtime.getRuntime().addShutdownHook(Thread { pg.close() }) }
        }

        fun postgres(): TestDatabase {
            // o esquema é declarado em maiúsculas (como sai do codegen em H2); no PostgreSQL os nomes são minúsculos
            val settings = Settings().withRenderSchema(false).withRenderNameCase(RenderNameCase.LOWER)
            return TestDatabase(SQLDialect.POSTGRES, embeddedPostgres.postgresDatabase, settings, "BYTEA")
        }
    }
}
