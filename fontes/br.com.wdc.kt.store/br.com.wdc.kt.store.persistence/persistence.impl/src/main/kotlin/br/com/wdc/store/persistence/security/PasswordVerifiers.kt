package br.com.wdc.store.persistence.security

import br.com.wdc.store.domain.security.JceCryptoProvider
import br.com.wdc.store.domain.security.PasswordUtil
import br.com.wdc.store.domain.security.Scram
import br.com.wdc.store.domain.security.ScramVerifier
import java.security.SecureRandom

/**
 * What the server stores for a password: a SCRAM-SHA-256 verifier with a fresh random salt ([ScramVerifier.encode]).
 * The only place that makes one — the user API, the seed data and the migration of the old digests use it.
 */
object PasswordVerifiers {

    private val crypto = JceCryptoProvider()
    private val scram = Scram(crypto)
    private val random = SecureRandom()

    /** The verifier of a password as the user typed it. */
    fun fromPassword(plain: String, iterations: Int = ScramVerifier.DEFAULT_ITERATIONS): String =
        fromDigest(PasswordUtil.hashPassword(plain, crypto), iterations)

    /** The verifier of a password known only by its old MD5 digest ([PasswordUtil.hashPassword]). */
    fun fromDigest(digest: String, iterations: Int = ScramVerifier.DEFAULT_ITERATIONS): String {
        val salt = ByteArray(ScramVerifier.SALT_SIZE).also(random::nextBytes)
        return scram.verifier(digest.trim(), salt, iterations).encode()
    }
}
