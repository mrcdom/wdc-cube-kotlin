package br.com.wdc.shopping.persistence.security

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.sql.SqlDataSource
import br.com.wdc.shopping.persistence.schema.EnUserIntentSecret
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent store for per-user HMAC intent signing secrets.
 *
 * On first access for a given user, generates a random 32-byte secret,
 * persists it in [EnUserIntentSecret], and caches it in memory.
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

        val ds = SqlDataSource.BEAN.get()
        ds.connection.use { conn ->
            // Try to load existing secret
            val en = EnUserIntentSecret.INSTANCE
            conn.prepareStatement("SELECT ${en.secret.name} FROM ${en.tableName()} WHERE ${en.userId.name} = ?").use { stmt ->
                stmt.setLong(1, userId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val secret = rs.getString(1)
                        cache[userId] = secret
                        return secret
                    }
                }
            }

            // Generate and persist a new secret
            val secret = generateSecret()
            conn.prepareStatement("INSERT INTO ${en.tableName()} (${en.userId.name}, ${en.secret.name}) VALUES (?, ?)").use { stmt ->
                stmt.setLong(1, userId)
                stmt.setString(2, secret)
                stmt.executeUpdate()
            }

            cache[userId] = secret
            LOG.info("Generated intent signing secret for userId: {}", userId)
            return secret
        }
    }

    private fun generateSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }
}
