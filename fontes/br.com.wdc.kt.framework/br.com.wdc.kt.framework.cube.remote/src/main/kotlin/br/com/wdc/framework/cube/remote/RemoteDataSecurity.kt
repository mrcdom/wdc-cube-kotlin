package br.com.wdc.framework.cube.remote

import br.com.wdc.framework.commons.log.Log
import com.ionspin.kotlin.bignum.integer.BigInteger
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * A session's cipher for sensitive values (a password typed in a form): AES-256-GCM with a key derived by PBKDF2
 * (250,000 iterations) from the password the client sent ciphered with the host's RSA key ([updateSecret]).
 */
class RemoteDataSecurity {

    private val security: RemoteAppSecurity = RemoteAppSecurity
    private val keyFactory: SecretKeyFactory

    private var secret: SecretKeySpec? = null
    private var iv: ByteArray? = null

    init {
        try {
            keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun updateSecret(signature: String) {
        try {
            val b64 = Base64.getUrlDecoder()
            val signatureParts = signature.split(".")

            val password: String
            run {
                val messageEncryptedAsBigInt = BigInteger.parseString(signatureParts[0], 36)
                val messageAsBigint = security.rsa.decrypt(messageEncryptedAsBigInt)
                val messageAsSafeBytes = messageAsBigint.toByteArray()
                val message = Base64.getDecoder().decode(messageAsSafeBytes)
                password = String(message, StandardCharsets.UTF_8)
            }

            val salt = b64.decode(signatureParts[1])
            this.iv = b64.decode(signatureParts[2])

            val spec = PBEKeySpec(password.toCharArray(), salt, 250000, 256)
            val secretKey = keyFactory.generateSecret(spec)
            this.secret = SecretKeySpec(secretKey.encoded, "AES")
        } catch (e: Exception) {
            val logger = Log.getLogger("RemoteDataSecurity")
            logger.error("updateSecret", e)
        }
    }

    // :: Binary Cypher

    fun cipher(binData: ByteArray): ByteArray {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmParameterSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secret, gcmParameterSpec)
            return cipher.doFinal(binData)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun decipher(binData: ByteArray): ByteArray {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmParameterSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secret, gcmParameterSpec)
            return cipher.doFinal(binData)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun b64Cipher(text: String): String {
        val textBytes = text.toByteArray(StandardCharsets.UTF_8)
        val cipheredTextBytes = cipher(textBytes)
        return Base64.getEncoder().encodeToString(cipheredTextBytes)
    }

    fun b64Decipher(b64Text: String): String {
        val cipheredTextBytes = Base64.getDecoder().decode(b64Text)
        val textBytes = decipher(cipheredTextBytes)
        return String(textBytes, StandardCharsets.UTF_8)
    }
}
