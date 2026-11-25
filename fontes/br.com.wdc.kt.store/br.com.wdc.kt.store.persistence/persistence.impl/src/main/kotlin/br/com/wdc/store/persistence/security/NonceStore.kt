package br.com.wdc.store.persistence.security

import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * The sign-in challenges in flight: each nonce is random, belongs to the user it was made for, is good for one
 * attempt ([consume] removes it) and expires in [NONCE_TTL].
 */
class NonceStore(private val clock: Clock = Clock.systemUTC()) {

    companion object {
        private val NONCE_TTL = Duration.ofSeconds(60)
        private val RANDOM = SecureRandom()
    }

    private class Pending(val userName: String, val expiresAt: Instant)

    private val nonces = ConcurrentHashMap<String, Pending>()

    /** A new nonce for [userName]. */
    fun generate(userName: String): String {
        val bytes = ByteArray(24).also(RANDOM::nextBytes)
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        nonces[nonce] = Pending(userName, Instant.now(clock).plus(NONCE_TTL))
        return nonce
    }

    fun expiresAt(nonce: String): Instant? = nonces[nonce]?.expiresAt

    /** Uses up [nonce]: `true` when it exists, has not expired and was made for [userName]. */
    fun consume(nonce: String, userName: String): Boolean {
        val pending = nonces.remove(nonce) ?: return false
        return pending.userName == userName && Instant.now(clock).isBefore(pending.expiresAt)
    }

    fun evictExpired() {
        val now = Instant.now(clock)
        nonces.entries.removeIf { now.isAfter(it.value.expiresAt) }
    }
}
