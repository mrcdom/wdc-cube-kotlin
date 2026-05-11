package br.com.wdc.shopping.scripts.sgbd

import org.jdbi.v3.core.Jdbi
import java.sql.Connection
import java.sql.SQLException

class Migration_0001_AddUserRoles(private val connection: Connection) {

    @Throws(SQLException::class)
    fun step01_addRolesColumn() {
        Jdbi.create(connection).open().use { handle ->
            handle.execute("ALTER TABLE EN_USER ADD COLUMN IF NOT EXISTS ROLES VARCHAR(255) DEFAULT 'CUSTOMER'")
        }
    }

    @Throws(SQLException::class)
    fun step02_setAdminRole() {
        Jdbi.create(connection).open().use { handle ->
            handle.execute("UPDATE EN_USER SET ROLES = 'ADMIN' WHERE USERNAME = 'admin'")
        }
    }
}
