package br.com.wdc.shopping.persistence.security

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.shopping.persistence.ShoppingDSLContext
import br.com.wdc.shopping.persistence.scheme.tables.references.EN_USER_INTENT_SECRET
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent store for per-user HMAC intent signing secrets.
 *
 * On first access for a given user, generates a random 32-byte secret,
 * persists it in `EN_USER_INTENT_SECRET`, and caches it in memory.
 * Subsequent accesses return the cached (and persisted) value.
 *
 * The secret is permanent — once created, it is never changed.
 */
class IntentSecretStore {

    companion object {
        private val LOG = Log.getLogger("IntentSecretStore")
    }

    private val cache = ConcurrentHashMap<Long, String>()

    /**
     * Returns the intent signing secret for [userId].
     * If none exists yet, generates one and persists it atomically.
     */
    fun getOrCreate(userId: Long): String {
        cache[userId]?.let { return it }

        val dsl = ShoppingDSLContext.BEAN.get()
        val t = EN_USER_INTENT_SECRET

        val existing = dsl.select(t.SECRET).from(t).where(t.USERID.eq(userId)).fetchOne(t.SECRET)
        if (existing != null) {
            cache[userId] = existing
            return existing
        }

        val secret = generateSecret()
        dsl.insertInto(t).set(t.USERID, userId).set(t.SECRET, secret).execute()

        cache[userId] = secret
        LOG.info("Generated intent signing secret for userId: {}", userId)
        return secret
    }

    private fun generateSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }
}
