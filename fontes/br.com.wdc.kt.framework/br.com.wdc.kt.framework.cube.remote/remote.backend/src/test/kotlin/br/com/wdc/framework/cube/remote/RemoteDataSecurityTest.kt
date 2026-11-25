package br.com.wdc.framework.cube.remote

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals

/** The session's cipher: the client's handshake, and an IV of its own for every value. */
class RemoteDataSecurityTest {

    private val random = SecureRandom()

    /** The client's side, as the browser bridge does it: a random password, its key, and the handshake. */
    private class Client(random: SecureRandom) {
        val key: SecretKeySpec
        val signature: String

        init {
            val password = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(12).also(random::nextBytes))
            val salt = ByteArray(16).also(random::nextBytes)
            val spec = PBEKeySpec(password.toCharArray(), salt, 250000, 256)
            key = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")

            val message = BigInteger.fromByteArray(Base64.getEncoder().encode(password.toByteArray()), Sign.POSITIVE)
            val ciphered = RemoteAppSecurity.rsa.encrypt(message).toString(36)
            signature = "$ciphered.${Base64.getUrlEncoder().withoutPadding().encodeToString(salt)}"
        }

        fun cipher(text: String, iv: ByteArray): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            return Base64.getEncoder().encodeToString(iv + cipher.doFinal(text.toByteArray()))
        }
    }

    private fun session(client: Client) = RemoteDataSecurity().apply { updateSecret(client.signature) }

    @Test
    fun theHostDeciphers_whatTheClientCiphered() {
        val client = Client(random)
        val iv = ByteArray(RemoteDataSecurity.IV_SIZE).also(random::nextBytes)
        assertEquals("fulano", session(client).b64Decipher(client.cipher("fulano", iv)))
    }

    @Test
    fun theSameValueTwice_isCipheredDifferently_andBothDecipher() {
        val security = session(Client(random))
        val first = security.b64Cipher("the same password")
        val second = security.b64Cipher("the same password")

        assertNotEquals(first, second, "a new IV for each value")
        assertNotEquals(
            Base64.getDecoder().decode(first).copyOf(RemoteDataSecurity.IV_SIZE).toList(),
            Base64.getDecoder().decode(second).copyOf(RemoteDataSecurity.IV_SIZE).toList(),
        )
        assertEquals("the same password", security.b64Decipher(first))
        assertEquals("the same password", security.b64Decipher(second))
    }

    @Test
    fun aChangedByte_isRefused() {
        val security = session(Client(random))
        val bytes = Base64.getDecoder().decode(security.b64Cipher("fulano"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertFails { security.b64Decipher(Base64.getEncoder().encodeToString(bytes)) }
    }

    @Test
    fun anotherSessionsKey_doesNotDecipher() {
        val ciphered = session(Client(random)).b64Cipher("fulano")
        assertFails { session(Client(random)).b64Decipher(ciphered) }
    }

    @Test
    fun withoutTheHandshake_nothingIsCiphered() {
        assertFails { RemoteDataSecurity().b64Cipher("fulano") }
    }
}
