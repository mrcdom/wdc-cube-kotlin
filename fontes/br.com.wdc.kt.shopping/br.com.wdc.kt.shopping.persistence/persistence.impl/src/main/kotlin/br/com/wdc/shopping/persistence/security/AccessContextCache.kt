package br.com.wdc.shopping.persistence.security

import br.com.wdc.framework.commons.log.Log
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AccessContextCache(
    val jwtSecret: String,
    private val intentSecretStore: IntentSecretStore,
    refreshTokenTtlDays: Int = 7,
) {

    companion object {
        private val LOG = Log.getLogger("AccessContextCache")
        private const val RSA_KEY_SIZE = 2048
        private val ACCESS_TOKEN_TTL = Duration.ofMinutes(30)

        private fun generateRsaKeyPair(): java.security.KeyPair {
            val gen = KeyPairGenerator.getInstance("RSA")
            gen.initialize(RSA_KEY_SIZE, SecureRandom())
            return gen.generateKeyPair()
        }
    }

    private val refreshTokenTtl: Duration = Duration.ofDays(refreshTokenTtlDays.toLong())

    private val sessionStore = SessionStore()
    private val bySessionId = ConcurrentHashMap<String, AccessContext>()
    private val refreshTokenIndex = ConcurrentHashMap<String, String>() // refreshToken → sessionId

    val accessTokenTtl: Duration get() = ACCESS_TOKEN_TTL

    fun createSession(userId: Long, userName: String, permissions: Set<String>): AccessContext {
        val keyPair = generateRsaKeyPair()
        val sessionId = UUID.randomUUID().toString()
        val refreshToken = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plus(ACCESS_TOKEN_TTL)
        val intentSignSecret = intentSecretStore.getOrCreate(userId)

        val ctx = AccessContext(sessionId, userId, userName, permissions, keyPair, expiresAt, refreshToken, intentSignSecret)

        bySessionId[sessionId] = ctx
        refreshTokenIndex[refreshToken] = sessionId

        sessionStore.save(ctx)

        LOG.info("Session created for user: {} ({}) sessionId={}", userName, userId, sessionId)
        return ctx
    }

    fun refresh(refreshToken: String): AccessContext? {
        // Try in-memory first, fall back to DB
        var sessionId = refreshTokenIndex[refreshToken]
        var existing: AccessContext? = if (sessionId != null) bySessionId[sessionId] else null

        if (existing == null) {
            // Try DB (e.g. after server restart)
            existing = sessionStore.findByRefreshToken(refreshToken)
            if (existing == null) return null
            sessionId = existing.sessionId
            // Load intentSignSecret (SessionStore doesn't store it)
            val intentSignSecret = intentSecretStore.getOrCreate(existing.userId!!)
            existing = AccessContext(
                existing.sessionId, existing.userId, existing.userName,
                existing.permissions, existing.rsaKeyPair, existing.expiresAt,
                existing.refreshToken, intentSignSecret
            )
        }

        if (existing.refreshToken != refreshToken) {
            refreshTokenIndex.remove(refreshToken)
            return null
        }

        val newSessionId = UUID.randomUUID().toString()
        val newRefreshToken = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plus(ACCESS_TOKEN_TTL)

        val intentSignSecret = intentSecretStore.getOrCreate(existing.userId!!)
        val ctx = AccessContext(newSessionId, existing.userId, existing.userName, existing.permissions,
            generateRsaKeyPair(), expiresAt, newRefreshToken, intentSignSecret)

        // Remove old session
        bySessionId.remove(sessionId)
        refreshTokenIndex.remove(refreshToken)
        sessionStore.deleteBySessionId(sessionId!!)

        // Add new session
        bySessionId[newSessionId] = ctx
        refreshTokenIndex[newRefreshToken] = newSessionId
        sessionStore.save(ctx)

        LOG.info("Session refreshed for user: {} ({}) newSessionId={}", existing.userName, existing.userId, newSessionId)
        return ctx
    }

    fun getBySessionId(sessionId: String): AccessContext? {
        // Try in-memory cache first
        var ctx = bySessionId[sessionId]
        if (ctx != null) {
            if (ctx.isExpired) {
                removeBySessionId(sessionId)
                return null
            }
            return ctx
        }

        // Fall back to DB (e.g. after server restart)
        ctx = sessionStore.findBySessionId(sessionId) ?: return null
        if (ctx.isExpired) {
            sessionStore.deleteBySessionId(sessionId)
            return null
        }

        // Load intentSignSecret (SessionStore doesn't store it)
        val intentSignSecret = intentSecretStore.getOrCreate(ctx.userId!!)
        ctx = AccessContext(
            ctx.sessionId, ctx.userId, ctx.userName,
            ctx.permissions, ctx.rsaKeyPair, ctx.expiresAt,
            ctx.refreshToken, intentSignSecret
        )

        // Cache it in memory
        bySessionId[sessionId] = ctx
        refreshTokenIndex[ctx.refreshToken] = sessionId
        return ctx
    }

    fun removeBySessionId(sessionId: String) {
        val ctx = bySessionId.remove(sessionId)
        if (ctx != null) {
            refreshTokenIndex.remove(ctx.refreshToken)
        }
        sessionStore.deleteBySessionId(sessionId)
        LOG.info("Session removed: sessionId={}", sessionId)
    }

    fun removeByRefreshToken(refreshToken: String): Boolean {
        val sessionId = refreshTokenIndex[refreshToken]
        if (sessionId != null) {
            removeBySessionId(sessionId)
            return true
        }

        // Try DB (session might not be in memory cache)
        val ctx = sessionStore.findByRefreshToken(refreshToken)
        if (ctx != null) {
            sessionStore.deleteByRefreshToken(refreshToken)
            LOG.info("Session removed from DB: refreshToken={}", refreshToken)
            return true
        }

        return false
    }

    fun evictExpired() {
        // Evict from in-memory cache
        val now = Instant.now()
        val it = bySessionId.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (now.isAfter(entry.value.expiresAt.plus(refreshTokenTtl))) {
                refreshTokenIndex.remove(entry.value.refreshToken)
                it.remove()
                LOG.debug("Evicted expired session from cache: sessionId={}", entry.key)
            }
        }

        // Evict from DB
        sessionStore.deleteExpired(now.minus(refreshTokenTtl))
    }
}
