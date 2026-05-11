package br.com.wdc.shopping.domain.security

import java.math.BigInteger
import java.nio.charset.StandardCharsets

object PasswordUtil {

    fun hashPassword(plainPassword: String): String {
        val input = plainPassword.toByteArray(StandardCharsets.UTF_8)
        val provider = CryptoProvider.BEAN.get()
            ?: throw IllegalStateException("CryptoProvider.BEAN not initialized")
        val digest = provider.md5(input)
        return BigInteger(digest).toString(36)
    }

    fun computeHmac(key: String, data: String): String {
        val keyBytes = key.toByteArray(StandardCharsets.UTF_8)
        val dataBytes = data.toByteArray(StandardCharsets.UTF_8)
        val provider = CryptoProvider.BEAN.get()
            ?: throw IllegalStateException("CryptoProvider.BEAN not initialized")
        val hash = provider.hmacSha256(keyBytes, dataBytes)
        return bytesToHex(hash)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
