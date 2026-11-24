package br.com.wdc.store.scripts.sgbd

import java.sql.Connection
import java.sql.SQLException
import org.jooq.SQLDialect

class Migration_0002_PurchaseBuyDateToTimestamp(private val connection: Connection) {

    @Throws(SQLException::class)
    fun step01_alterBuyDateToTimestamp() {
        // only the old H2 databases had the column with another type; in PostgreSQL it is born TIMESTAMP
        if (DBCreate.detectDialect(connection) != SQLDialect.H2) return
        connection.createStatement().use { stmt ->
            stmt.execute("ALTER TABLE EN_PURCHASE ALTER COLUMN BUYDATE TIMESTAMP NOT NULL")
        }
    }
}
