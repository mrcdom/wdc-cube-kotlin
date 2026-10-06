package br.com.wdc.shopping.scripts.sgbd

import java.sql.Connection
import java.sql.SQLException

class Migration_0001_AddUserRoles(private val connection: Connection) {

    @Throws(SQLException::class)
    fun step01_addRolesColumn() {
        connection.createStatement().use { stmt ->
            stmt.execute("ALTER TABLE EN_USER ADD COLUMN IF NOT EXISTS ROLES VARCHAR(255) DEFAULT 'CUSTOMER'")
        }
    }

    @Throws(SQLException::class)
    fun step02_setAdminRole() {
        connection.createStatement().use { stmt ->
            stmt.execute("UPDATE EN_USER SET ROLES = 'ADMIN' WHERE USERNAME = 'admin'")
        }
    }
}
