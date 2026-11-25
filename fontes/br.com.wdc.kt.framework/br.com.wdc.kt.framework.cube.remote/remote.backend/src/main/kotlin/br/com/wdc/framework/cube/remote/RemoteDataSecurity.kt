package br.com.wdc.framework.cube.remote

import br.com.wdc.framework.commons.log.Log
import com.ionspin.kotlin.bignum.integer.BigInteger
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * A session's cipher for sensitive values (a password typed in a form): AES-256-GCM with a key derived by PBKDF2
 * (250,000 iterations) from the password the client sent ciphered with the host's RSA key ([updateSecret]).
 *
 * Every value is ciphered with an IV of its own, random, sent in front of it: `Base64(IV ‖ ciphertext and tag)`. One
 * IV for the whole session would let whoever sees two values learn their XOR and forge the tag.
 */
class RemoteDataSecurity {

    companion object {
        /** Bytes of the IV in front of every ciphered value (the size GCM is made for). */
        const val IV_SIZE = 12

        private const val TAG_BITS = 128
    }

    private val security: RemoteAppSecurity = RemoteAppSecurity
    private val keyFactory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    private val random = SecureRandom()

    @Volatile
    private var secret: SecretKeySpec? = null

    /**
     * Takes the session's key from the client's handshake: `{password ciphered with the host's RSA, base 36}.{salt,
     * Base64 URL}`. A malformed one leaves the key as it was (and is logged).
     */
    fun updateSecret(signature: String) {
        try {
            val parts = signature.split(".")
            require(parts.size >= 2) { "The secret must be <password>.<salt>" }

            val messageAsBigint = security.rsa.decrypt(BigInteger.parseString(parts[0], 36))
            val password = String(Base64.getDecoder().decode(messageAsBigint.toByteArray()), StandardCharsets.UTF_8)
            val salt = Base64.getUrlDecoder().decode(parts[1])

            val spec = PBEKeySpec(password.toCharArray(), salt, 250000, 256)
            this.secret = SecretKeySpec(keyFactory.generateSecret(spec).encoded, "AES")
        } catch (e: Exception) {
            Log.getLogger("RemoteDataSecurity").error("updateSecret", e)
        }
    }

    // :: Binary Cypher

    /** Ciphers [binData] with a new random IV, which goes in front of the result. */
    fun cipher(binData: ByteArray): ByteArray {
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(binData)
    }

    /** Deciphers what [cipher] (or the client) made: the IV, then the ciphertext and its tag. A changed byte fails. */
    fun decipher(binData: ByteArray): ByteArray {
        require(binData.size > IV_SIZE) { "Too short to be a ciphered value" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, binData, 0, IV_SIZE))
        return cipher.doFinal(binData, IV_SIZE, binData.size - IV_SIZE)
    }

    private fun key(): SecretKeySpec = secret ?: throw IllegalStateException("No session secret yet")

    fun b64Cipher(text: String): String =
        Base64.getEncoder().encodeToString(cipher(text.toByteArray(StandardCharsets.UTF_8)))

    fun b64Decipher(b64Text: String): String =
        String(decipher(Base64.getDecoder().decode(b64Text)), StandardCharsets.UTF_8)
}
