package br.com.wdc.store.scripts.sgbd

import java.sql.Connection
import java.sql.SQLException

/**
 * The sign-in challenges in flight, in the database instead of one instance's memory: a challenge answered by one
 * instance and a login sent to another find the same nonce. `EXPIRES_AT` in UTC, like the other timestamps.
 */
class Migration_0010_AuthNonceTable(private val connection: Connection) {

    @Throws(SQLException::class)
    fun step01_createAuthNonceTable() {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS EN_AUTH_NONCE (
                    NONCE VARCHAR(64) NOT NULL,
                    USERNAME VARCHAR(100) NOT NULL,
                    EXPIRES_AT TIMESTAMP NOT NULL,
                    CONSTRAINT PK_AUTH_NONCE PRIMARY KEY (NONCE)
                )""",
            )
        }
    }
}
