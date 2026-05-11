package br.com.wdc.shopping.domain.security

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class JceCryptoProvider : CryptoProvider {

    override fun md5(input: ByteArray): ByteArray =
        MessageDigest.getInstance("MD5").digest(input)

    override fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return mac.doFinal(data)
    }

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
    }
}
