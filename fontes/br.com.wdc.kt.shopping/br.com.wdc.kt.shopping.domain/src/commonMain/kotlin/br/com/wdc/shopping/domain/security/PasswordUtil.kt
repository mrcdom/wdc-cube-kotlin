package br.com.wdc.shopping.domain.security

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign

object PasswordUtil {

    fun hashPassword(plainPassword: String): String {
        val input = plainPassword.encodeToByteArray()
        val provider = CryptoProvider.BEAN.get()
            ?: throw IllegalStateException("CryptoProvider.BEAN not initialized")
        val digest = provider.md5(input)
        return BigInteger.fromByteArray(digest, Sign.POSITIVE).toString(36)
    }

    fun computeHmac(key: String, data: String): String {
        val keyBytes = key.encodeToByteArray()
        val dataBytes = data.encodeToByteArray()
        val provider = CryptoProvider.BEAN.get()
            ?: throw IllegalStateException("CryptoProvider.BEAN not initialized")
        val hash = provider.hmacSha256(keyBytes, dataBytes)
        return bytesToHex(hash)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val unsigned = b.toInt() and 0xFF
            sb.append(unsigned.toString(16).padStart(2, '0'))
        }
        return sb.toString()
    }
}
