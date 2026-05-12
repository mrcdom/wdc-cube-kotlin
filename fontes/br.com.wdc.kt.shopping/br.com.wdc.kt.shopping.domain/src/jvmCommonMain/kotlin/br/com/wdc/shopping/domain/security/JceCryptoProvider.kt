package br.com.wdc.shopping.domain.security

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
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

    override fun rsaEncryptOaep(publicKeyBase64: String, data: ByteArray): ByteArray {
        val keyBytes = java.util.Base64.getDecoder().decode(publicKeyBase64)
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(keyBytes))
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(data)
    }

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
    }
}
