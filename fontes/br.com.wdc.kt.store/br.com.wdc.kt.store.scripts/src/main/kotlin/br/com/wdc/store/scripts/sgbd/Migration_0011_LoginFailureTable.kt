package br.com.wdc.store.scripts.sgbd

import java.sql.Connection
import java.sql.SQLException

/**
 * The failed sign-ins per user, in the database instead of one instance's memory: with several instances, a guesser
 * spreading attempts over them is slowed down as if there were one. `LAST_FAILURE` in UTC, like the other timestamps.
 */
class Migration_0011_LoginFailureTable(private val connection: Connection) {

    @Throws(SQLException::class)
    fun step01_createLoginFailureTable() {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS EN_LOGIN_FAILURE (
                    USERNAME VARCHAR(100) NOT NULL,
                    FAILURES INTEGER NOT NULL,
                    LAST_FAILURE TIMESTAMP NOT NULL,
                    CONSTRAINT PK_LOGIN_FAILURE PRIMARY KEY (USERNAME)
                )""",
            )
        }
    }
}
