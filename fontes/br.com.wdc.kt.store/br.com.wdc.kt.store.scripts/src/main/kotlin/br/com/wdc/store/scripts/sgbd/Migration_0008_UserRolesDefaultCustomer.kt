package br.com.wdc.store.scripts.sgbd

import java.sql.Connection
import java.sql.SQLException

/**
 * A user written without roles is a customer, in every database.
 *
 * Migration 0001 added `ROLES` with `DEFAULT 'CUSTOMER'`, but [DBCreate] created the column without it: the databases
 * created by it (new H2 ones, PostgreSQL) left such a user with no role, and so with no access to the data. Setting
 * the default again is harmless where it is already there.
 */
class Migration_0008_UserRolesDefaultCustomer(private val connection: Connection) {

    @Throws(SQLException::class)
    fun step01_setRolesDefault() {
        connection.createStatement().use { stmt ->
            stmt.execute("ALTER TABLE EN_USER ALTER COLUMN ROLES SET DEFAULT 'CUSTOMER'")
        }
    }
}
