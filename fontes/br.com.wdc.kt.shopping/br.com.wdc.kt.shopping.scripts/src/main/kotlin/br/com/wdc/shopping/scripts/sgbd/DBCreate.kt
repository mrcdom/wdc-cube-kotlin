package br.com.wdc.shopping.scripts.sgbd

import br.com.wdc.framework.jooq.JsonDialect
import java.sql.Connection
import java.sql.SQLException
import org.jooq.SQLDialect

/**
 * Cria o esquema do banco e roda as migrações pendentes.
 *
 * **O DDL daqui é a fonte de verdade do esquema.** As classes jOOQ de `:shopping-persistence`
 * (`…persistence.scheme`) são geradas a partir dele, por `GenerateJooqSchema`. Ao mudar uma tabela: altere
 * aqui, escreva a migração que leva os bancos existentes ao mesmo resultado e regenere as classes.
 */
class DBCreate {
    private var connection: Connection? = null
    private var mustResetDb = false
    private var skipReset = false

    fun withConnection(connection: Connection): DBCreate {
        this.connection = connection
        return this
    }

    /** Apaga os dados e recarrega a carga de demonstração. */
    fun withReset(): DBCreate {
        this.mustResetDb = true
        return this
    }

    /** Não carrega dados, mesmo num banco recém-criado (uso do gerador de classes jOOQ). */
    fun withSkipReset(): DBCreate {
        this.skipReset = true
        return this
    }

    @Throws(SQLException::class)
    fun run(): DBCreate {
        val conn = connection ?: throw IllegalStateException("Connection not set")
        val dialect = detectDialect(conn)
        val postgres = dialect == SQLDialect.POSTGRES

        JsonDialect.of(dialect).initialize(conn)

        val existing = loadExistingTables(conn)

        if ("EN_MIGRATION_LOG" !in existing) {
            conn.execute(
                """
                CREATE TABLE EN_MIGRATION_LOG (
                    ID BIGINT NOT NULL,
                    SCRIPT_NAME VARCHAR(255) NOT NULL,
                    STEP_NAME VARCHAR(255) NOT NULL,
                    EXECUTED_AT TIMESTAMP NOT NULL,
                    CONSTRAINT PK_MIGRATION_LOG PRIMARY KEY (ID)
                )""",
                "CREATE SEQUENCE IF NOT EXISTS SQ_MIGRATION_LOG START WITH 1 INCREMENT BY 1",
            )
        }

        if ("EN_USER" !in existing) {
            conn.execute(
                """
                CREATE TABLE EN_USER (
                    ID BIGINT NOT NULL,
                    USERNAME VARCHAR(255) NOT NULL,
                    PASSWORD CHAR(32) NOT NULL,
                    NAME VARCHAR(255) NOT NULL,
                    ROLES VARCHAR(255),
                    CONSTRAINT PK_USER PRIMARY KEY (ID)
                )""",
                "CREATE SEQUENCE IF NOT EXISTS SQ_USER START WITH 1 INCREMENT BY 1",
                // sustentam as ordenações NAME_A_TO_Z e LOGIN_A_TO_Z de UserCriteria
                "CREATE INDEX IX_USER_NAME ON EN_USER (NAME)",
                "CREATE INDEX IX_USER_USERNAME ON EN_USER (USERNAME)",
            )
            mustResetDb = true
        }

        if ("EN_PRODUCT" !in existing) {
            val nameType = if (postgres) "TEXT" else "VARCHAR_IGNORECASE(1000000)"
            val descriptionType = if (postgres) "TEXT" else "VARCHAR(1000000)"
            // VARBINARY, e não BINARY: no H2 2.x o BINARY é de tamanho fixo e completa o valor com zeros
            val imageType = if (postgres) "BYTEA" else "VARBINARY(1000000)"
            conn.execute(
                """
                CREATE TABLE EN_PRODUCT (
                    ID BIGINT NOT NULL,
                    NAME $nameType NOT NULL,
                    PRICE NUMERIC(20,2) NOT NULL,
                    DESCRIPTION $descriptionType NOT NULL,
                    IMAGE $imageType,
                    CONSTRAINT PK_PRODUCT PRIMARY KEY (ID)
                )""",
                "CREATE SEQUENCE IF NOT EXISTS SQ_PRODUCT START WITH 1 INCREMENT BY 1",
                // sustentam NAME_A_TO_Z, CHEAPEST_FIRST e MOST_EXPENSIVE_FIRST de ProductCriteria
                "CREATE INDEX IX_PRODUCT_NAME ON EN_PRODUCT (NAME)",
                "CREATE INDEX IX_PRODUCT_PRICE ON EN_PRODUCT (PRICE)",
            )
            mustResetDb = true
        }

        if ("EN_PURCHASE" !in existing) {
            conn.execute(
                """
                CREATE TABLE EN_PURCHASE (
                    ID BIGINT NOT NULL,
                    USERID BIGINT NOT NULL,
                    BUYDATE TIMESTAMP NOT NULL,
                    CONSTRAINT PK_PURCHASE PRIMARY KEY (ID),
                    CONSTRAINT FK_PURCHASE_USER FOREIGN KEY (USERID) REFERENCES EN_USER(ID)
                )""",
                "CREATE SEQUENCE IF NOT EXISTS SQ_PURCHASE START WITH 1 INCREMENT BY 1",
                // sustenta MOST_RECENT_PURCHASE_FIRST e EARLIEST_PURCHASE_FIRST de PurchaseCriteria
                "CREATE INDEX IX_PURCHASE_BUYDATE ON EN_PURCHASE (BUYDATE)",
            )
            mustResetDb = true
        }

        if ("EN_PURCHASEITEM" !in existing) {
            conn.execute(
                """
                CREATE TABLE EN_PURCHASEITEM (
                    ID BIGINT NOT NULL,
                    PURCHASEID BIGINT NOT NULL,
                    PRODUCTID BIGINT NOT NULL,
                    AMOUNT INT NOT NULL,
                    PRICE NUMERIC(20,2) NOT NULL,
                    CONSTRAINT PK_PURCHASEITEM PRIMARY KEY (ID),
                    CONSTRAINT FK_PURCHASEITEM_PRODUCT FOREIGN KEY (PRODUCTID) REFERENCES EN_PRODUCT(ID),
                    CONSTRAINT FK_PURCHASEITEM_PURCHASE FOREIGN KEY (PURCHASEID) REFERENCES EN_PURCHASE(ID)
                )""",
                "CREATE SEQUENCE IF NOT EXISTS SQ_PURCHASEITEM START WITH 1 INCREMENT BY 1",
                // sustentam MOST_EXPENSIVE_FIRST e LARGEST_QUANTITY_FIRST de PurchaseItemCriteria
                "CREATE INDEX IX_PURCHASEITEM_PRICE ON EN_PURCHASEITEM (PRICE)",
                "CREATE INDEX IX_PURCHASEITEM_AMOUNT ON EN_PURCHASEITEM (AMOUNT)",
            )
            mustResetDb = true
        }

        if ("EN_USER_INTENT_SECRET" !in existing) {
            conn.execute(
                """
                CREATE TABLE EN_USER_INTENT_SECRET (
                    USERID BIGINT NOT NULL,
                    SECRET VARCHAR(64) NOT NULL,
                    CONSTRAINT PK_USER_INTENT_SECRET PRIMARY KEY (USERID),
                    CONSTRAINT FK_UIS_USER FOREIGN KEY (USERID) REFERENCES EN_USER(ID)
                )""",
            )
        }

        if ("EN_USER_SESSION" !in existing) {
            conn.execute(
                """
                CREATE TABLE EN_USER_SESSION (
                    SESSION_ID VARCHAR(36) NOT NULL,
                    USERID BIGINT NOT NULL,
                    USERNAME VARCHAR(100) NOT NULL,
                    REFRESH_TOKEN VARCHAR(36) NOT NULL,
                    EXPIRES_AT TIMESTAMP NOT NULL,
                    PERMISSIONS VARCHAR(4000),
                    RSA_PUBLIC_KEY VARCHAR(2000) NOT NULL,
                    RSA_PRIVATE_KEY VARCHAR(4000) NOT NULL,
                    CONSTRAINT PK_USER_SESSION PRIMARY KEY (SESSION_ID),
                    CONSTRAINT FK_US_USER FOREIGN KEY (USERID) REFERENCES EN_USER(ID),
                    CONSTRAINT UQ_US_REFRESH UNIQUE (REFRESH_TOKEN)
                )""",
            )
        }

        if (mustResetDb && !skipReset) {
            DBReset.run(conn)
        }

        // As migrações levam os bancos criados por versões anteriores ao esquema acima; num banco novo, não mudam nada.
        MigrationRunner(conn)
            .run(Migration_0001_AddUserRoles(conn))
            .run(Migration_0002_PurchaseBuyDateToTimestamp(conn))
            .run(Migration_0003_CreateSecurityTables(conn))
            .run(Migration_0004_ImageVarbinaryAndOrderingIndexes(conn))

        return this
    }

    private fun loadExistingTables(conn: Connection): Set<String> {
        val tables = HashSet<String>()
        conn.metaData.getTables(null, null, "%", arrayOf("TABLE")).use { rs ->
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME").uppercase())
            }
        }
        return tables
    }

    private fun Connection.execute(vararg statements: String) {
        createStatement().use { stmt -> statements.forEach { stmt.execute(it.trimIndent()) } }
    }

    companion object {
        /** O banco, pela URL da conexão: PostgreSQL ou, por padrão, H2. */
        fun detectDialect(connection: Connection): SQLDialect {
            val url = try {
                connection.metaData.url
            } catch (_: SQLException) {
                null
            }
            return if (url != null && (url.startsWith("jdbc:postgresql:") || url.startsWith("jdbc:pgsql:"))) {
                SQLDialect.POSTGRES
            } else {
                SQLDialect.H2
            }
        }
    }
}
