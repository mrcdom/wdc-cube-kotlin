package br.com.wdc.shopping.persistence.security

import br.com.wdc.framework.commons.log.Log
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AccessContextCache(val jwtSecret: String) {

    companion object {
        private val LOG = Log.getLogger("AccessContextCache")
        private const val RSA_KEY_SIZE = 2048
        private val ACCESS_TOKEN_TTL = Duration.ofMinutes(30)
        private val REFRESH_TOKEN_TTL = Duration.ofDays(7)

        private fun generateRsaKeyPair(): java.security.KeyPair {
            val gen = KeyPairGenerator.getInstance("RSA")
            gen.initialize(RSA_KEY_SIZE, SecureRandom())
            return gen.generateKeyPair()
        }
    }

    private val byUserId = ConcurrentHashMap<Long, AccessContext>()
    private val refreshTokenIndex = ConcurrentHashMap<String, Long>()

    val accessTokenTtl: Duration get() = ACCESS_TOKEN_TTL

    fun createSession(userId: Long, userName: String, permissions: Set<String>): AccessContext {
        val keyPair = generateRsaKeyPair()
        val refreshToken = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plus(ACCESS_TOKEN_TTL)

        val ctx = AccessContext(userId, userName, permissions, keyPair, expiresAt, refreshToken)

        val previous = byUserId.put(userId, ctx)
        if (previous != null) {
            refreshTokenIndex.remove(previous.refreshToken)
        }
        refreshTokenIndex[refreshToken] = userId

        LOG.info("Session created for user: {} ({})", userName, userId)
        return ctx
    }

    fun refresh(refreshToken: String): AccessContext? {
        val userId = refreshTokenIndex[refreshToken] ?: return null
        val existing = byUserId[userId]
        if (existing == null || existing.refreshToken != refreshToken) {
            refreshTokenIndex.remove(refreshToken)
            return null
        }

        val newRefreshToken = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plus(ACCESS_TOKEN_TTL)

        val ctx = AccessContext(userId, existing.userName, existing.permissions,
            generateRsaKeyPair(), expiresAt, newRefreshToken)

        byUserId[userId] = ctx
        refreshTokenIndex.remove(refreshToken)
        refreshTokenIndex[newRefreshToken] = userId

        LOG.info("Session refreshed for user: {} ({})", existing.userName, userId)
        return ctx
    }

    fun get(userId: Long): AccessContext? {
        val ctx = byUserId[userId]
        if (ctx != null && ctx.isExpired) {
            remove(userId)
            return null
        }
        return ctx
    }

    fun remove(userId: Long) {
        val ctx = byUserId.remove(userId)
        if (ctx != null) {
            refreshTokenIndex.remove(ctx.refreshToken)
            LOG.info("Session removed for user: {}", userId)
        }
    }

    fun removeByRefreshToken(refreshToken: String): Boolean {
        val userId = refreshTokenIndex[refreshToken] ?: return false
        remove(userId)
        return true
    }

    fun evictExpired() {
        val now = Instant.now()
        val it = byUserId.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (now.isAfter(entry.value.expiresAt.plus(REFRESH_TOKEN_TTL))) {
                refreshTokenIndex.remove(entry.value.refreshToken)
                it.remove()
                LOG.debug("Evicted expired session for userId: {}", entry.key)
            }
        }
    }
}
