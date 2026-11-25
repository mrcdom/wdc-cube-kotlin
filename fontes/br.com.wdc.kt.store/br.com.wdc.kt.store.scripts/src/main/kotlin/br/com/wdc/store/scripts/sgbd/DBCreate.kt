package br.com.wdc.store.scripts.sgbd

import br.com.wdc.framework.jooq.JsonDialect
import java.sql.Connection
import java.sql.SQLException
import org.jooq.SQLDialect

/**
 * Creates the database schema and runs the pending migrations.
 *
 * **The DDL here is the source of truth of the schema.** The jOOQ classes of `:store-persistence`
 * (`…persistence.scheme`) are generated from it, by `GenerateJooqSchema`. When changing a table: change it
 * here, write the migration that brings existing databases to the same result and regenerate the classes.
 */
class DBCreate {
    private var connection: Connection? = null
    private var mustResetDb = false
    private var skipReset = false

    fun withConnection(connection: Connection): DBCreate {
        this.connection = connection
        return this
    }

    /** Deletes the data and reloads the demo seed. */
    fun withReset(): DBCreate {
        this.mustResetDb = true
        return this
    }

    /** Loads no data, even into a freshly created database (used by the jOOQ class generator). */
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
                    ROLES VARCHAR(255) DEFAULT 'CUSTOMER',
                    CONSTRAINT PK_USER PRIMARY KEY (ID)
                )""",
                "CREATE SEQUENCE IF NOT EXISTS SQ_USER START WITH 1 INCREMENT BY 1",
                // back the NAME_A_TO_Z and LOGIN_A_TO_Z orderings of UserCriteria
                "CREATE INDEX IX_USER_NAME ON EN_USER (NAME)",
                "CREATE INDEX IX_USER_USERNAME ON EN_USER (USERNAME)",
            )
            mustResetDb = true
        }

        if ("EN_PRODUCT" !in existing) {
            val nameType = if (postgres) "TEXT" else "VARCHAR_IGNORECASE(1000000)"
            val descriptionType = if (postgres) "TEXT" else "VARCHAR(1000000)"
            // VARBINARY, not BINARY: in H2 2.x BINARY has a fixed size and pads the value with zeros
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
                // back NAME_A_TO_Z, CHEAPEST_FIRST and MOST_EXPENSIVE_FIRST of ProductCriteria
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
                // backs MOST_RECENT_PURCHASE_FIRST and EARLIEST_PURCHASE_FIRST of PurchaseCriteria
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
                // back MOST_EXPENSIVE_FIRST and LARGEST_QUANTITY_FIRST of PurchaseItemCriteria
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

        // The migrations bring databases created by earlier versions to the schema above; on a new database they change nothing.
        MigrationRunner(conn)
            .run(Migration_0001_AddUserRoles(conn))
            .run(Migration_0002_PurchaseBuyDateToTimestamp(conn))
            .run(Migration_0003_CreateSecurityTables(conn))
            .run(Migration_0004_ImageVarbinaryAndOrderingIndexes(conn))
            .run(Migration_0005_UnsignedPasswordDigest(conn))
            .run(Migration_0006_PurchaseBuyDateToUtc(conn))
            .run(Migration_0007_SessionExpiryToUtc(conn))
            .run(Migration_0008_UserRolesDefaultCustomer(conn))

        // The seed comes after the migrations: it already writes in the current format, and must not be migrated.
        if (mustResetDb && !skipReset) {
            DBReset.run(conn)
        }

        return this
    }

    private fun loadExistingTables(conn: Connection): Set<String> {
        val tables = HashSet<String>()
        // In PostgreSQL only the tables of the current schema count: the same database may have another schema with the same tables.
        val schema = if (detectDialect(conn) == SQLDialect.POSTGRES) conn.schema else null
        conn.metaData.getTables(null, schema, "%", arrayOf("TABLE")).use { rs ->
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
        /** The database, from the connection URL: PostgreSQL or, by default, H2. */
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
