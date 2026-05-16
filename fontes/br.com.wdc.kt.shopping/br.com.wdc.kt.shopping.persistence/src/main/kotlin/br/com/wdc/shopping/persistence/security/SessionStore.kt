package br.com.wdc.shopping.persistence.security

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.sql.SqlDataSource
import br.com.wdc.shopping.persistence.schema.EnUserSession
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64

/**
 * Persistent store for user sessions in H2.
 *
 * Each session is stored with its full state (RSA key pair, permissions, refresh token, etc.)
 * so that it can be reconstructed after a server restart.
 */
class SessionStore {

    companion object {
        private val LOG = Log.getLogger("SessionStore")

        private fun encodeKeyPair(keyPair: KeyPair): Pair<String, String> {
            val pubBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
            val privBase64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)
            return pubBase64 to privBase64
        }

        private fun decodeKeyPair(pubBase64: String, privBase64: String): KeyPair {
            val kf = KeyFactory.getInstance("RSA")
            val publicKey = kf.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(pubBase64)))
            val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(privBase64)))
            return KeyPair(publicKey, privateKey)
        }

        private fun permissionsToString(permissions: Set<String>): String? {
            return if (permissions.isEmpty()) null else permissions.joinToString(",")
        }

        private fun stringToPermissions(s: String?): Set<String> {
            return if (s.isNullOrBlank()) emptySet() else s.split(",").toSet()
        }
    }

    fun save(session: AccessContext) {
        val ds = SqlDataSource.BEAN.get()
        val en = EnUserSession.INSTANCE
        val (pubKey, privKey) = encodeKeyPair(session.rsaKeyPair)

        ds.connection.use { conn ->
            conn.prepareStatement(
                "MERGE INTO ${en.tableName()} (${en.sessionId.name}, ${en.userId.name}, ${en.userName.name}, " +
                        "${en.refreshToken.name}, ${en.expiresAt.name}, ${en.permissions.name}, " +
                        "${en.rsaPublicKey.name}, ${en.rsaPrivateKey.name}) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            ).use { stmt ->
                stmt.setString(1, session.sessionId)
                stmt.setLong(2, session.userId!!)
                stmt.setString(3, session.userName!!)
                stmt.setString(4, session.refreshToken)
                stmt.setTimestamp(5, Timestamp.from(session.expiresAt))
                stmt.setString(6, permissionsToString(session.permissions))
                stmt.setString(7, pubKey)
                stmt.setString(8, privKey)
                stmt.executeUpdate()
            }
        }
    }

    fun findBySessionId(sessionId: String): AccessContext? {
        val ds = SqlDataSource.BEAN.get()
        val en = EnUserSession.INSTANCE

        ds.connection.use { conn ->
            conn.prepareStatement(
                "SELECT ${en.sessionId.name}, ${en.userId.name}, ${en.userName.name}, " +
                        "${en.refreshToken.name}, ${en.expiresAt.name}, ${en.permissions.name}, " +
                        "${en.rsaPublicKey.name}, ${en.rsaPrivateKey.name} " +
                        "FROM ${en.tableName()} WHERE ${en.sessionId.name} = ?"
            ).use { stmt ->
                stmt.setString(1, sessionId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) return mapRow(rs)
                }
            }
        }
        return null
    }

    fun findByRefreshToken(refreshToken: String): AccessContext? {
        val ds = SqlDataSource.BEAN.get()
        val en = EnUserSession.INSTANCE

        ds.connection.use { conn ->
            conn.prepareStatement(
                "SELECT ${en.sessionId.name}, ${en.userId.name}, ${en.userName.name}, " +
                        "${en.refreshToken.name}, ${en.expiresAt.name}, ${en.permissions.name}, " +
                        "${en.rsaPublicKey.name}, ${en.rsaPrivateKey.name} " +
                        "FROM ${en.tableName()} WHERE ${en.refreshToken.name} = ?"
            ).use { stmt ->
                stmt.setString(1, refreshToken)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) return mapRow(rs)
                }
            }
        }
        return null
    }

    fun deleteBySessionId(sessionId: String) {
        val ds = SqlDataSource.BEAN.get()
        val en = EnUserSession.INSTANCE

        ds.connection.use { conn ->
            conn.prepareStatement(
                "DELETE FROM ${en.tableName()} WHERE ${en.sessionId.name} = ?"
            ).use { stmt ->
                stmt.setString(1, sessionId)
                stmt.executeUpdate()
            }
        }
    }

    fun deleteByRefreshToken(refreshToken: String) {
        val ds = SqlDataSource.BEAN.get()
        val en = EnUserSession.INSTANCE

        ds.connection.use { conn ->
            conn.prepareStatement(
                "DELETE FROM ${en.tableName()} WHERE ${en.refreshToken.name} = ?"
            ).use { stmt ->
                stmt.setString(1, refreshToken)
                stmt.executeUpdate()
            }
        }
    }

    fun deleteExpired(cutoff: Instant) {
        val ds = SqlDataSource.BEAN.get()
        val en = EnUserSession.INSTANCE

        ds.connection.use { conn ->
            conn.prepareStatement(
                "DELETE FROM ${en.tableName()} WHERE ${en.expiresAt.name} < ?"
            ).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(cutoff))
                val deleted = stmt.executeUpdate()
                if (deleted > 0) {
                    LOG.debug("Evicted {} expired sessions from DB", deleted)
                }
            }
        }
    }

    private fun mapRow(rs: java.sql.ResultSet): AccessContext {
        val sid = rs.getString(1)
        val userId = rs.getLong(2)
        val userName = rs.getString(3)
        val refreshToken = rs.getString(4)
        val expiresAt = rs.getTimestamp(5).toInstant()
        val permissions = stringToPermissions(rs.getString(6))
        val pubKey = rs.getString(7)
        val privKey = rs.getString(8)
        val keyPair = decodeKeyPair(pubKey, privKey)

        return AccessContext(sid, userId, userName, permissions, keyPair, expiresAt, refreshToken, "")
    }
}
