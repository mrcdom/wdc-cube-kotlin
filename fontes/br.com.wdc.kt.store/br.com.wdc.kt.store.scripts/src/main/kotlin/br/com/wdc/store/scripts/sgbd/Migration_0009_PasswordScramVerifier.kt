package br.com.wdc.store.scripts.sgbd

import br.com.wdc.store.domain.security.ScramVerifier
import br.com.wdc.store.persistence.security.PasswordVerifiers
import java.sql.Connection
import java.sql.SQLException

/**
 * Replaces the stored password digests (unsalted MD5, base 36) with SCRAM-SHA-256 verifiers.
 *
 * The digest was the key of the old login: whoever read it could sign in as the user, and an unsalted MD5 is cheap to
 * reverse. The verifier is derived from the digest itself ([PasswordVerifiers.fromDigest]) with a salt per user and
 * PBKDF2, so the conversion needs no password and every user keeps signing in with the same one; the digest is gone.
 */
class Migration_0009_PasswordScramVerifier(private val connection: Connection) {

    /** A verifier does not fit the old `CHAR(32)`. */
    @Throws(SQLException::class)
    fun step01_widenPasswordColumn() {
        connection.createStatement().use { stmt ->
            stmt.execute("ALTER TABLE EN_USER ALTER COLUMN PASSWORD SET DATA TYPE VARCHAR(255)")
        }
    }

    @Throws(SQLException::class)
    fun step02_digestsToVerifiers() {
        val digests = HashMap<Long, String>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT ID, PASSWORD FROM EN_USER").use { rs ->
                while (rs.next()) {
                    // the CHAR(32) column padded the digest with spaces
                    val stored = rs.getString(2)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                    if (ScramVerifier.parse(stored) == null) digests[rs.getLong(1)] = stored
                }
            }
        }
        connection.prepareStatement("UPDATE EN_USER SET PASSWORD = ? WHERE ID = ?").use { ps ->
            for ((id, digest) in digests) {
                ps.setString(1, PasswordVerifiers.fromDigest(digest))
                ps.setLong(2, id)
                ps.executeUpdate()
            }
        }
    }
}
