package br.com.wdc.shopping.persistence.security

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.shopping.persistence.ShoppingDSLContext
import br.com.wdc.shopping.persistence.scheme.tables.references.EN_USER_SESSION
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.jooq.DSLContext
import org.jooq.Record
import java.util.Base64

/**
 * Persistent store for user sessions.
 *
 * Each session is stored with its full state (RSA key pair, permissions, refresh token, etc.)
 * so that it can be reconstructed after a server restart. `EXPIRES_AT` is stored in UTC.
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

    private fun dsl(): DSLContext = ShoppingDSLContext.BEAN.get()

    fun save(session: AccessContext) {
        val t = EN_USER_SESSION
        val (pubKey, privKey) = encodeKeyPair(session.rsaKeyPair)
        val expiresAt = LocalDateTime.ofInstant(session.expiresAt, ZoneOffset.UTC)
        val permissions = permissionsToString(session.permissions)

        dsl().insertInto(t)
            .set(t.SESSION_ID, session.sessionId)
            .set(t.USERID, session.userId!!)
            .set(t.USERNAME, session.userName!!)
            .set(t.REFRESH_TOKEN, session.refreshToken)
            .set(t.EXPIRES_AT, expiresAt)
            .set(t.PERMISSIONS, permissions)
            .set(t.RSA_PUBLIC_KEY, pubKey)
            .set(t.RSA_PRIVATE_KEY, privKey)
            .onConflict(t.SESSION_ID)
            .doUpdate()
            .set(t.USERID, session.userId!!)
            .set(t.USERNAME, session.userName!!)
            .set(t.REFRESH_TOKEN, session.refreshToken)
            .set(t.EXPIRES_AT, expiresAt)
            .set(t.PERMISSIONS, permissions)
            .set(t.RSA_PUBLIC_KEY, pubKey)
            .set(t.RSA_PRIVATE_KEY, privKey)
            .execute()
    }

    fun findBySessionId(sessionId: String): AccessContext? =
        dsl().selectFrom(EN_USER_SESSION).where(EN_USER_SESSION.SESSION_ID.eq(sessionId)).fetchOne()?.let(::mapRow)

    fun findByRefreshToken(refreshToken: String): AccessContext? =
        dsl().selectFrom(EN_USER_SESSION).where(EN_USER_SESSION.REFRESH_TOKEN.eq(refreshToken)).fetchOne()?.let(::mapRow)

    fun deleteBySessionId(sessionId: String) {
        dsl().deleteFrom(EN_USER_SESSION).where(EN_USER_SESSION.SESSION_ID.eq(sessionId)).execute()
    }

    fun deleteByRefreshToken(refreshToken: String) {
        dsl().deleteFrom(EN_USER_SESSION).where(EN_USER_SESSION.REFRESH_TOKEN.eq(refreshToken)).execute()
    }

    fun deleteExpired(cutoff: Instant) {
        val deleted = dsl().deleteFrom(EN_USER_SESSION)
            .where(EN_USER_SESSION.EXPIRES_AT.lt(LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC)))
            .execute()
        if (deleted > 0) {
            LOG.debug("Evicted {} expired sessions from DB", deleted)
        }
    }

    private fun mapRow(row: Record): AccessContext {
        val t = EN_USER_SESSION
        val keyPair = decodeKeyPair(row.get(t.RSA_PUBLIC_KEY)!!, row.get(t.RSA_PRIVATE_KEY)!!)
        return AccessContext(
            row.get(t.SESSION_ID)!!,
            row.get(t.USERID)!!,
            row.get(t.USERNAME)!!,
            stringToPermissions(row.get(t.PERMISSIONS)),
            keyPair,
            row.get(t.EXPIRES_AT)!!.toInstant(ZoneOffset.UTC),
            row.get(t.REFRESH_TOKEN)!!,
            "",
        )
    }
}
