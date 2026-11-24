package br.com.wdc.store.persistence.security

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.store.persistence.StoreDSLContext
import br.com.wdc.store.persistence.scheme.tables.references.EN_USER_SESSION
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
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
 * Each session is stored with its full state (RSA key pair, role names, refresh token, etc.)
 * so that it can be reconstructed after a server restart. `EXPIRES_AT` is stored in UTC.
 *
 * **The RSA private key is stored encrypted** (AES-256-GCM, `v1:` + Base64 of IV and ciphertext), with a key derived
 * from the JWT secret: a copy of the table alone does not give the sessions' keys. A session whose key was stored in
 * the clear (before 0.5.0) is not restored.
 *
 * The role names go in the `PERMISSIONS` column, which kept `entity:operation` permissions before the data policies
 * (0.5.0). A session stored in that form is not restored: it is treated as missing, so its client signs in again.
 */
class SessionStore(jwtSecret: String) {

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

        private fun rolesToString(roles: Set<String>): String? {
            return if (roles.isEmpty()) null else roles.joinToString(",")
        }

        private fun stringToRoles(s: String?): Set<String> {
            return if (s.isNullOrBlank()) emptySet() else s.split(",").toSet()
        }

        private const val ENCRYPTED_PREFIX = "v1:"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128

        /** A session stored before 0.5.0 keeps `entity:operation` permissions instead of role names. */
        private fun isLegacy(s: String?): Boolean = s != null && ':' in s
    }

    /** The key that encrypts the private keys: HMAC-SHA256 of a fixed label with the JWT secret. */
    private val keyCipher: SecretKeySpec = run {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(jwtSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        SecretKeySpec(mac.doFinal("session-private-key".toByteArray(StandardCharsets.UTF_8)), "AES")
    }

    private val random = SecureRandom()

    private fun encryptPrivateKey(plain: String): String {
        val iv = ByteArray(GCM_IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyCipher, GCMParameterSpec(GCM_TAG_BITS, iv))
        val sealed = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(iv + sealed)
    }

    /** The private key, or `null` if it was stored in the clear (before 0.5.0) or doesn't decrypt with this secret. */
    private fun decryptPrivateKey(stored: String): String? {
        if (!stored.startsWith(ENCRYPTED_PREFIX)) return null
        return try {
            val bytes = Base64.getDecoder().decode(stored.substring(ENCRYPTED_PREFIX.length))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keyCipher, GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_BYTES))
            String(cipher.doFinal(bytes, GCM_IV_BYTES, bytes.size - GCM_IV_BYTES), StandardCharsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun dsl(): DSLContext = StoreDSLContext.BEAN.get()

    fun save(session: AccessContext) {
        val t = EN_USER_SESSION
        val (pubKey, plainPrivKey) = encodeKeyPair(session.rsaKeyPair)
        val privKey = encryptPrivateKey(plainPrivKey)
        val expiresAt = LocalDateTime.ofInstant(session.expiresAt, ZoneOffset.UTC)
        val roles = rolesToString(session.roles)

        dsl().insertInto(t)
            .set(t.SESSION_ID, session.sessionId)
            .set(t.USERID, session.userId!!)
            .set(t.USERNAME, session.userName!!)
            .set(t.REFRESH_TOKEN, session.refreshToken)
            .set(t.EXPIRES_AT, expiresAt)
            .set(t.PERMISSIONS, roles)
            .set(t.RSA_PUBLIC_KEY, pubKey)
            .set(t.RSA_PRIVATE_KEY, privKey)
            .onConflict(t.SESSION_ID)
            .doUpdate()
            .set(t.USERID, session.userId!!)
            .set(t.USERNAME, session.userName!!)
            .set(t.REFRESH_TOKEN, session.refreshToken)
            .set(t.EXPIRES_AT, expiresAt)
            .set(t.PERMISSIONS, roles)
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

    private fun mapRow(row: Record): AccessContext? {
        val t = EN_USER_SESSION
        if (isLegacy(row.get(t.PERMISSIONS))) {
            LOG.info("Session {} predates the role names: not restored", row.get(t.SESSION_ID))
            return null
        }
        val privateKey = decryptPrivateKey(row.get(t.RSA_PRIVATE_KEY)!!) ?: run {
            LOG.info("Session {} has no private key this server can read: not restored", row.get(t.SESSION_ID))
            return null
        }
        val keyPair = decodeKeyPair(row.get(t.RSA_PUBLIC_KEY)!!, privateKey)
        return AccessContext(
            row.get(t.SESSION_ID)!!,
            row.get(t.USERID)!!,
            row.get(t.USERNAME)!!,
            stringToRoles(row.get(t.PERMISSIONS)),
            keyPair,
            row.get(t.EXPIRES_AT)!!.toInstant(ZoneOffset.UTC),
            row.get(t.REFRESH_TOKEN)!!,
            "",
        )
    }
}
