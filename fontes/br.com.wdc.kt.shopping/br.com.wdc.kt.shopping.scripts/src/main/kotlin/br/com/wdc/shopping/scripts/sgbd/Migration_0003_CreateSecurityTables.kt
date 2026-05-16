package br.com.wdc.shopping.scripts.sgbd

import br.com.wdc.shopping.persistence.schema.EnUserIntentSecret
import br.com.wdc.shopping.persistence.schema.EnUserSession
import java.sql.Connection
import java.sql.SQLException

class Migration_0003_CreateSecurityTables(private val connection: Connection) {

    @Throws(SQLException::class)
    fun step01_createUserIntentSecretTable() {
        connection.createStatement().use { stmt ->
            stmt.execute(EnUserIntentSecret.INSTANCE.createTableSql())
        }
    }

    @Throws(SQLException::class)
    fun step02_createUserSessionTable() {
        connection.createStatement().use { stmt ->
            stmt.execute(EnUserSession.INSTANCE.createTableSql())
        }
    }
}
