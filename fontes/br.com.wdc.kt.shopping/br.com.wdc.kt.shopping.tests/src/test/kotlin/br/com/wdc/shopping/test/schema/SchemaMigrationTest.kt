package br.com.wdc.shopping.test.schema

import br.com.wdc.shopping.scripts.sgbd.DBCreate
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.scripts.sgbd.Migration_0006_PurchaseBuyDateToUtc
import java.sql.Connection
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * As migrações têm de levar um banco criado antes da adoção do jOOQ ao mesmo esquema que [DBCreate] cria num
 * banco novo — é o que permite gerar as classes jOOQ de um e usá-las no outro.
 */
class SchemaMigrationTest {

    private fun newDatabase(): Connection =
        DriverManager.getConnection("jdbc:h2:mem:schema-${counter.incrementAndGet()};DB_CLOSE_DELAY=-1", "sa", "sa")

    private fun Connection.createLegacySchema() {
        val script = SchemaMigrationTest::class.java.getResource("/legacy-schema-pre-jooq.sql")!!.readText()
        createStatement().use { stmt ->
            script.split(";").map { it.lines().filterNot { l -> l.trim().startsWith("--") }.joinToString("\n").trim() }
                .filter { it.isNotEmpty() }
                .forEach { stmt.execute(it) }
        }
        // as três migrações que o banco de origem já tinha aplicado
        prepareStatement(
            "INSERT INTO EN_MIGRATION_LOG (ID, SCRIPT_NAME, STEP_NAME, EXECUTED_AT) VALUES (NEXT VALUE FOR SQ_MIGRATION_LOG, ?, ?, CURRENT_TIMESTAMP)"
        ).use { ps ->
            listOf(
                "Migration_0001_AddUserRoles" to "step01_addRolesColumn",
                "Migration_0001_AddUserRoles" to "step02_setAdminRole",
                "Migration_0002_PurchaseBuyDateToTimestamp" to "step01_alterBuyDateToTimestamp",
                "Migration_0003_CreateSecurityTables" to "step01_createUserIntentSecretTable",
                "Migration_0003_CreateSecurityTables" to "step02_createUserSessionTable",
            ).forEach { (script, step) ->
                ps.setString(1, script); ps.setString(2, step); ps.executeUpdate()
            }
        }
    }

    private fun Connection.rows(sql: String): List<String> = createStatement().use { stmt ->
        stmt.executeQuery(sql).use { rs ->
            val columns = rs.metaData.columnCount
            buildList { while (rs.next()) add((1..columns).joinToString(" | ") { rs.getString(it) ?: "null" }) }
        }
    }

    private fun Connection.columns() = rows(
        """SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE, IS_NULLABLE
           FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC' ORDER BY TABLE_NAME, COLUMN_NAME"""
    )

    private fun Connection.constraints() = rows(
        """SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
           WHERE TABLE_SCHEMA = 'PUBLIC' ORDER BY TABLE_NAME, CONSTRAINT_NAME"""
    )

    private fun Connection.orderingIndexes() = rows(
        """SELECT I.TABLE_NAME, I.INDEX_NAME, C.COLUMN_NAME FROM INFORMATION_SCHEMA.INDEXES I
           JOIN INFORMATION_SCHEMA.INDEX_COLUMNS C ON C.INDEX_SCHEMA = I.INDEX_SCHEMA AND C.INDEX_NAME = I.INDEX_NAME
           WHERE I.TABLE_SCHEMA = 'PUBLIC' AND I.INDEX_NAME LIKE 'IX\_%' ESCAPE '\' ORDER BY I.INDEX_NAME"""
    )

    private fun Connection.sequences() =
        rows("SELECT SEQUENCE_NAME FROM INFORMATION_SCHEMA.SEQUENCES WHERE SEQUENCE_SCHEMA = 'PUBLIC' ORDER BY 1")

    @Test
    fun migratedLegacyDatabase_hasTheSameSchemaAsANewOne() {
        newDatabase().use { fresh ->
            newDatabase().use { legacy ->
                DBCreate().withConnection(fresh).withSkipReset().run()

                legacy.createLegacySchema()
                DBCreate().withConnection(legacy).run()

                assertEquals(fresh.columns().joinToString("\n"), legacy.columns().joinToString("\n"))
                assertEquals(fresh.constraints(), legacy.constraints())
                assertEquals(fresh.orderingIndexes(), legacy.orderingIndexes())
                assertEquals(fresh.sequences(), legacy.sequences())
            }
        }
    }

    @Test
    fun newDatabase_hasTheOrderingIndexes_andAVariableLengthImage() {
        newDatabase().use { fresh ->
            DBCreate().withConnection(fresh).withSkipReset().run()
            assertEquals(
                listOf(
                    "EN_PRODUCT | IX_PRODUCT_NAME | NAME",
                    "EN_PRODUCT | IX_PRODUCT_PRICE | PRICE",
                    "EN_PURCHASEITEM | IX_PURCHASEITEM_AMOUNT | AMOUNT",
                    "EN_PURCHASEITEM | IX_PURCHASEITEM_PRICE | PRICE",
                    "EN_PURCHASE | IX_PURCHASE_BUYDATE | BUYDATE",
                    "EN_USER | IX_USER_NAME | NAME",
                    "EN_USER | IX_USER_USERNAME | USERNAME",
                ),
                fresh.orderingIndexes(),
            )
            assertTrue(fresh.columns().any { it.startsWith("EN_PRODUCT | IMAGE | BINARY VARYING | 1000000") }, fresh.columns().toString())
        }
    }

    @Test
    fun migration_removesTheZeroPaddingOfLegacyImages() {
        newDatabase().use { legacy ->
            legacy.createLegacySchema()
            val image = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0, 7, 0x82.toByte())
            legacy.prepareStatement("INSERT INTO EN_PRODUCT (ID, NAME, PRICE, DESCRIPTION, IMAGE) VALUES (?, ?, 1, 'd', ?)").use { ps ->
                ps.setLong(1, 1); ps.setString(2, "com imagem"); ps.setBytes(3, image); ps.executeUpdate()
                ps.setLong(1, 2); ps.setString(2, "sem imagem"); ps.setNull(3, java.sql.Types.BINARY); ps.executeUpdate()
            }
            // a coluna BINARY de tamanho fixo completou a imagem com zeros
            assertEquals(listOf("1000000"), legacy.rows("SELECT OCTET_LENGTH(IMAGE) FROM EN_PRODUCT WHERE ID = 1"))

            DBCreate().withConnection(legacy).run()

            legacy.createStatement().use { stmt ->
                stmt.executeQuery("SELECT ID, IMAGE FROM EN_PRODUCT ORDER BY ID").use { rs ->
                    rs.next(); assertContentEquals(image, rs.getBytes(2))
                    rs.next(); assertNull(rs.getBytes(2))
                }
            }
        }
    }

    @Test
    fun migration_rewritesSignedPasswordDigests_asTheUnsignedOnesTheApplicationComputes() {
        newDatabase().use { legacy ->
            legacy.createLegacySchema()
            legacy.prepareStatement("INSERT INTO EN_USER (ID, USERNAME, PASSWORD, NAME, ROLES) VALUES (?, ?, ?, ?, 'CUSTOMER')").use { ps ->
                // como a carga antiga gravava: MD5 lido com sinal, em base 36
                listOf(
                    Triple(0L, "admin", "1ymiigxvce4vzea4zp5bsfbgj"),      // bit de sinal 0: já coincide
                    Triple(2L, "beotrano", "-17msdx5ah76k0tdyvaoieqemg"),   // bit de sinal 1: negativo
                    Triple(7L, "externo", "senha-gravada-sem-resumo"),      // gravada pela API, sem resumo: fica como está
                ).forEach { (id, user, digest) ->
                    ps.setLong(1, id); ps.setString(2, user); ps.setString(3, digest); ps.setString(4, user); ps.executeUpdate()
                }
            }

            DBCreate().withConnection(legacy).run()

            assertEquals(
                listOf("admin | 1ymiigxvce4vzea4zp5bsfbgj", "beotrano | dxz5j4uooih4r59rlath82ago", "externo | senha-gravada-sem-resumo"),
                legacy.rows("SELECT USERNAME, TRIM(PASSWORD) FROM EN_USER ORDER BY ID"),
            )
            assertEquals(DBReset.passwordDigest("beotrano"), "dxz5j4uooih4r59rlath82ago")
        }
    }

    @Test
    fun migration_movesLegacyPurchaseDates_fromLocalWallTimeToUtc() {
        newDatabase().use { legacy ->
            legacy.createLegacySchema()
            legacy.createStatement().use { stmt ->
                stmt.execute("INSERT INTO EN_USER (ID, USERNAME, PASSWORD, NAME, ROLES) VALUES (0, 'admin', 'x', 'Admin', 'ADMIN')")
                // como a persistência antiga gravava: a hora local da máquina
                stmt.execute("INSERT INTO EN_PURCHASE (ID, USERID, BUYDATE) VALUES (0, 0, TIMESTAMP '2024-01-15 10:30:00')")
                stmt.execute("INSERT INTO EN_PURCHASE (ID, USERID, BUYDATE) VALUES (1, 0, TIMESTAMP '2024-07-15 10:30:00')")
            }
            DBCreate().withConnection(legacy).run()

            val zone = ZoneId.systemDefault()
            fun utcOf(local: String) =
                LocalDateTime.parse(local).atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime().toString()
            assertEquals(
                listOf(utcOf("2024-01-15T10:30:00"), utcOf("2024-07-15T10:30:00")),
                legacy.rows("SELECT FORMATDATETIME(BUYDATE, 'yyyy-MM-dd''T''HH:mm') FROM EN_PURCHASE ORDER BY ID").map { LocalDateTime.parse(it).toString() },
            )
        }
    }

    @Test
    fun migration_convertsWithTheZoneTheDataWasWrittenIn() {
        newDatabase().use { legacy ->
            legacy.createLegacySchema()
            legacy.createStatement().use { stmt ->
                stmt.execute("INSERT INTO EN_USER (ID, USERNAME, PASSWORD, NAME, ROLES) VALUES (0, 'admin', 'x', 'Admin', 'ADMIN')")
                stmt.execute("INSERT INTO EN_PURCHASE (ID, USERID, BUYDATE) VALUES (0, 0, TIMESTAMP '2024-01-15 10:30:00')")
                stmt.execute("INSERT INTO EN_PURCHASE (ID, USERID, BUYDATE) VALUES (1, 0, TIMESTAMP '2024-07-15 10:30:00')")
            }
            Migration_0006_PurchaseBuyDateToUtc(legacy, ZoneId.of("Europe/Paris")).step01_localWallTimeToUtc()

            // inverno: UTC+1; verão: UTC+2
            assertEquals(
                listOf("2024-01-15 09:30", "2024-07-15 08:30"),
                legacy.rows("SELECT FORMATDATETIME(BUYDATE, 'yyyy-MM-dd HH:mm') FROM EN_PURCHASE ORDER BY ID"),
            )
        }
    }

    @Test
    fun newDatabase_seedsPurchaseDatesInUtc_andMigrationsDoNotShiftThem() {
        newDatabase().use { db ->
            DBCreate().withConnection(db).run()
            DBCreate().withConnection(db).run()
            assertEquals(
                listOf("2010-01-01 14:30", "2011-04-03 09:15"),
                db.rows("SELECT FORMATDATETIME(BUYDATE, 'yyyy-MM-dd HH:mm') FROM EN_PURCHASE ORDER BY ID"),
            )
        }
    }

    @Test
    fun runningTwice_changesNothing() {
        newDatabase().use { db ->
            DBCreate().withConnection(db).run()
            val columns = db.columns()
            val users = db.rows("SELECT COUNT(*) FROM EN_USER")
            val migrations = db.rows("SELECT COUNT(*) FROM EN_MIGRATION_LOG")

            DBCreate().withConnection(db).run()

            assertEquals(columns, db.columns())
            assertEquals(users, db.rows("SELECT COUNT(*) FROM EN_USER"))
            assertEquals(migrations, db.rows("SELECT COUNT(*) FROM EN_MIGRATION_LOG"))
        }
    }

    @Test
    fun seededImages_areStoredWithTheirRealSize() {
        newDatabase().use { db ->
            DBCreate().withConnection(db).run()
            val sizes = db.rows("SELECT OCTET_LENGTH(IMAGE) FROM EN_PRODUCT WHERE IMAGE IS NOT NULL").map { it.toInt() }
            assertTrue(sizes.isNotEmpty())
            assertTrue(sizes.all { it in 1 until 1_000_000 }, sizes.toString())
        }
    }

    private companion object {
        val counter = AtomicInteger()
    }
}
