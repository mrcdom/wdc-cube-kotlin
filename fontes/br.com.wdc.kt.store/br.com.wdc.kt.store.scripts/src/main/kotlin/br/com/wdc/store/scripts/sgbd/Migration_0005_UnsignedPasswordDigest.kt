package br.com.wdc.store.scripts.sgbd

import java.math.BigInteger
import java.sql.Connection
import java.sql.SQLException

/**
 * Rewrites the password digests that the old demo seed stored as signed.
 *
 * The digest is the MD5 of the password in base 36. The seed read it as a **signed** integer; the application
 * (`PasswordUtil.hashPassword`) computes it **unsigned**. Both match when the first bit of the MD5 is 0;
 * when it is 1, the seed stored a negative number and the user could never authenticate.
 */
class Migration_0005_UnsignedPasswordDigest(private val connection: Connection) {

    @Throws(SQLException::class)
    fun step01_rewriteSignedDigests() {
        val signed = HashMap<Long, String>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT ID, PASSWORD FROM EN_USER").use { rs ->
                while (rs.next()) {
                    val digest = rs.getString(2)?.trim() ?: continue
                    // only what has the shape of a signed digest: '-' followed by base-36 digits
                    if (SIGNED_DIGEST.matches(digest)) {
                        signed[rs.getLong(1)] = digest
                    }
                }
            }
        }
        connection.prepareStatement("UPDATE EN_USER SET PASSWORD = ? WHERE ID = ?").use { ps ->
            for ((id, digest) in signed) {
                // the same 128-bit pattern, read as unsigned
                ps.setString(1, BigInteger(digest, 36).add(TWO_POW_128).toString(36))
                ps.setLong(2, id)
                ps.executeUpdate()
            }
        }
    }

    private companion object {
        val SIGNED_DIGEST = Regex("-[0-9a-z]{1,25}")
        val TWO_POW_128: BigInteger = BigInteger.ONE.shiftLeft(128)
    }
}
