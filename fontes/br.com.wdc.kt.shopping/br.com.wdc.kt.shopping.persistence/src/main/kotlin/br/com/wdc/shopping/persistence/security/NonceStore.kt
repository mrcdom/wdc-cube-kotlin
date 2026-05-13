package br.com.wdc.shopping.persistence.security

import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class NonceStore {

    companion object {
        private val NONCE_TTL = Duration.ofSeconds(60)
    }

    private val nonces = ConcurrentHashMap<String, Instant>()

    fun generate(): String {
        val nonce = UUID.randomUUID().toString()
        nonces[nonce] = Instant.now().plus(NONCE_TTL)
        return nonce
    }

    fun expiresAt(nonce: String): Instant? = nonces[nonce]

    fun consume(nonce: String): Boolean {
        val expiresAt = nonces.remove(nonce) ?: return false
        return Instant.now().isBefore(expiresAt)
    }

    fun evictExpired() {
        val now = Instant.now()
        val it = nonces.entries.iterator()
        while (it.hasNext()) {
            if (now.isAfter(it.next().value)) {
                it.remove()
            }
        }
    }
}
