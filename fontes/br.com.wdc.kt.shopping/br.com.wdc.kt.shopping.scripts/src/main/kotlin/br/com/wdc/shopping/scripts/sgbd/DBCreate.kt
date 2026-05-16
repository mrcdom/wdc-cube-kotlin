package br.com.wdc.shopping.scripts.sgbd

import br.com.wdc.shopping.persistence.schema.EnProduct
import br.com.wdc.shopping.persistence.schema.EnPurchase
import br.com.wdc.shopping.persistence.schema.EnPurchaseItem
import br.com.wdc.shopping.persistence.schema.EnUser
import br.com.wdc.shopping.scripts.sgbd.schema.EnMigrationLog
import java.sql.Connection
import java.sql.SQLException

class DBCreate {

    private var connection: Connection? = null
    private var mustResetDb = false

    fun withConnection(connection: Connection): DBCreate {
        this.connection = connection
        return this
    }

    fun withReset(): DBCreate {
        this.mustResetDb = true
        return this
    }

    @Throws(SQLException::class)
    fun run(): DBCreate {
        val conn = connection ?: throw IllegalStateException("Connection not set")
        val tableMap = loadTableMap(conn)

        val enMigrationLog = EnMigrationLog.INSTANCE
        if (!tableMap.containsKey("PUBLIC.${enMigrationLog.tableName()}")) {
            createTable(conn, enMigrationLog)
        }

        val enUser = EnUser.INSTANCE
        if (!tableMap.containsKey("PUBLIC.${enUser.tableName()}")) {
            createTable(conn, enUser)
            mustResetDb = true
        }

        val enProduct = EnProduct.INSTANCE
        if (!tableMap.containsKey("PUBLIC.${enProduct.tableName()}")) {
            createTable(conn, enProduct)
            mustResetDb = true
        }

        val enPurchase = EnPurchase.INSTANCE
        if (!tableMap.containsKey("PUBLIC.${enPurchase.tableName()}")) {
            createTable(conn, enPurchase)
            mustResetDb = true
        }

        val enPurchaseItem = EnPurchaseItem.INSTANCE
        if (!tableMap.containsKey("PUBLIC.${enPurchaseItem.tableName()}")) {
            createTable(conn, enPurchaseItem)
            mustResetDb = true
        }

        if (mustResetDb) {
            DBReset.run(conn)
        }

        MigrationRunner(conn)
            .run(Migration_0001_AddUserRoles(conn))
            .run(Migration_0002_PurchaseBuyDateToTimestamp(conn))
            .run(Migration_0003_CreateSecurityTables(conn))

        return this
    }

    private fun loadTableMap(conn: Connection): Map<String, Boolean> {
        val tableMap = sortedMapOf<String, Boolean>(String.CASE_INSENSITIVE_ORDER)
        conn.metaData.getTables(null, null, "%", arrayOf("TABLE")).use { rs ->
            while (rs.next()) {
                val tableSchem = rs.getString("TABLE_SCHEM")
                val tableName = rs.getString("TABLE_NAME")
                val key = buildString {
                    if (!tableSchem.isNullOrBlank()) {
                        append(tableSchem)
                        append(".")
                    }
                    append(tableName)
                }
                tableMap[key] = true
            }
        }
        return tableMap
    }

    private fun createTable(conn: Connection, table: br.com.wdc.shopping.persistence.schema.support.DbTable) {
        conn.createStatement().use { stmt ->
            stmt.execute(table.createTableSql())
            try {
                stmt.execute(table.createSequenceSql())
            } catch (_: UnsupportedOperationException) {
                // Table has no sequence (e.g. EN_USER_INTENT_SECRET)
            }
        }
    }
}
