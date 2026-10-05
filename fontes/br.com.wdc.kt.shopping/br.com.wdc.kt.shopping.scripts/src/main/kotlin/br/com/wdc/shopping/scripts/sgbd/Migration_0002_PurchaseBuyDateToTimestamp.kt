package br.com.wdc.shopping.scripts.sgbd

import java.sql.Connection
import java.sql.SQLException
import org.jooq.SQLDialect

class Migration_0002_PurchaseBuyDateToTimestamp(private val connection: Connection) {

    @Throws(SQLException::class)
    fun step01_alterBuyDateToTimestamp() {
        // só os bancos H2 antigos tiveram a coluna com outro tipo; no PostgreSQL ela já nasce TIMESTAMP
        if (DBCreate.detectDialect(connection) != SQLDialect.H2) return
        connection.createStatement().use { stmt ->
            stmt.execute("ALTER TABLE EN_PURCHASE ALTER COLUMN BUYDATE TIMESTAMP NOT NULL")
        }
    }
}
