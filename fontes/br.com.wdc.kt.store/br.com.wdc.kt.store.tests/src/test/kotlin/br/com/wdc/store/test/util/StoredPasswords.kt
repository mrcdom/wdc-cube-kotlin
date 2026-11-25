package br.com.wdc.store.test.util

import br.com.wdc.store.domain.security.JceCryptoProvider
import br.com.wdc.store.domain.security.PasswordUtil
import br.com.wdc.store.domain.security.Scram
import br.com.wdc.store.domain.security.ScramVerifier

/** What the tests check of a stored password: that it is a verifier, and of which password. */
object StoredPasswords {

    private val crypto = JceCryptoProvider()
    private val scram = Scram(crypto)

    /** Whether [stored] is the SCRAM-SHA-256 verifier of [password]. */
    fun matches(stored: String?, password: String): Boolean {
        val verifier = ScramVerifier.parse(stored) ?: return false
        val salted = scram.saltedPassword(PasswordUtil.hashPassword(password, crypto), verifier.salt, verifier.iterations)
        return Scram.constantTimeEquals(scram.storedKey(scram.clientKey(salted)), verifier.storedKey)
    }
}
