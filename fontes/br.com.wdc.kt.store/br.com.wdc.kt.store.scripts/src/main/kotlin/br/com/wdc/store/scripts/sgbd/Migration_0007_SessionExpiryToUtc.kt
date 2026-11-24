package br.com.wdc.store.scripts.sgbd

import java.sql.Connection
import java.sql.SQLException

/**
 * Discards the sessions stored with the expiry in the machine's local time.
 *
 * `EXPIRES_AT` is a TIMESTAMP without time zone and now stores the instant in UTC, like the other dates. Old
 * sessions would expire at the wrong time; since a session is disposable state, they go — the user authenticates
 * again.
 */
class Migration_0007_SessionExpiryToUtc(private val connection: Connection) {

    @Throws(SQLException::class)
    fun step01_dropLocalTimeSessions() {
        connection.createStatement().use { stmt ->
            stmt.execute("DELETE FROM EN_USER_SESSION")
        }
    }
}
