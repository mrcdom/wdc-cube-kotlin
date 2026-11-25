package br.com.wdc.store.domain.security

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign

object PasswordUtil {

    /**
     * The password's MD5 digest, read as an unsigned integer in base 36: the form the application stored before
     * SCRAM. It is now only the input of the salted key derivation ([Scram.saltedPassword]), which keeps the old
     * digests convertible into verifiers; it is never stored nor sent.
     */
    fun hashPassword(plainPassword: String, provider: CryptoProvider? = CryptoProvider.BEAN.get()): String {
        val crypto = provider ?: throw IllegalStateException("CryptoProvider.BEAN not initialized")
        val digest = crypto.md5(plainPassword.encodeToByteArray())
        return BigInteger.fromByteArray(digest, Sign.POSITIVE).toString(36)
    }
}
