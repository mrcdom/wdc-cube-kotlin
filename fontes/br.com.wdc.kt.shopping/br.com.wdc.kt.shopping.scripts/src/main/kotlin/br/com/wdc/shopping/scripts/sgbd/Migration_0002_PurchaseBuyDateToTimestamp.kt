package br.com.wdc.shopping.scripts.sgbd

import org.jdbi.v3.core.Jdbi
import java.sql.Connection
import java.sql.SQLException

class Migration_0002_PurchaseBuyDateToTimestamp(private val connection: Connection) {

    @Throws(SQLException::class)
    fun step01_alterBuyDateToTimestamp() {
        Jdbi.create(connection).open().use { handle ->
            handle.execute("ALTER TABLE EN_PURCHASE ALTER COLUMN BUYDATE TIMESTAMP NOT NULL")
        }
    }
}
