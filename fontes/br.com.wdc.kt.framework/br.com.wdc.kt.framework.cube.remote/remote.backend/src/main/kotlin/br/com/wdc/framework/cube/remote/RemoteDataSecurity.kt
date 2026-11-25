package br.com.wdc.framework.cube.remote

import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A session's cipher for sensitive values (a password typed in a form), on top of TLS.
 *
 * **The key.** Each connection agrees on its own: the client sends its ECDH P-256 public key (`clientKey`), the host
 * makes a key pair for the session and answers with its public key (`serverKey`), and both derive the AES-256-GCM key
 * from the shared secret with HKDF-SHA256 — the session id as salt, [INFO] as info. Neither side keeps a long-lived
 * key: a recorded session cannot be opened later, even with the server's secrets (forward secrecy). Public keys travel
 * as Base64 SubjectPublicKeyInfo (what Web Crypto exports as `spki`).
 *
 * **The values.** Each one is ciphered with an IV of its own, random, sent in front of it: `Base64(IV ‖ ciphertext and
 * tag)`. One IV for the whole session would let whoever sees two values learn their XOR and forge the tag.
 */
class RemoteDataSecurity(private val sessionId: String) {

    companion object {
        /** Bytes of the IV in front of every ciphered value (the size GCM is made for). */
        const val IV_SIZE = 12

        /** The HKDF info: what the derived key is for, and the version of this scheme. */
        const val INFO = "wdc-cube-remote/aes-256-gcm/v1"

        private const val CURVE = "secp256r1"
        private const val TAG_BITS = 128
    }

    private val random = SecureRandom()

    @Volatile
    private var secret: SecretKeySpec? = null

    /** Whether a handshake ran: values can be ciphered. */
    val ready: Boolean get() = secret != null

    /**
     * Agrees on the session's key with the client's public key ([clientKey], Base64 SPKI of a P-256 key) and returns
     * the host's public key for the client, in the same form. A later handshake (a reconnection) replaces the key.
     */
    fun handshake(clientKey: String): String {
        val keyFactory = KeyFactory.getInstance("EC")
        val clientPublic = keyFactory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(clientKey)))

        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec(CURVE), random) }
        val serverPair = generator.generateKeyPair()
        val serverPublic = serverPair.public as ECPublicKey
        require(clientPublic is ECPublicKey && clientPublic.params.order == serverPublic.params.order) {
            "The client's key is not a P-256 key"
        }

        val shared = KeyAgreement.getInstance("ECDH").run {
            init(serverPair.private)
            doPhase(clientPublic, true)
            generateSecret()
        }
        secret = SecretKeySpec(hkdf(ikm = shared, salt = sessionId.toByteArray(StandardCharsets.UTF_8)), "AES")
        return Base64.getEncoder().encodeToString(serverPublic.encoded)
    }

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

    fun b64Cipher(text: String): String =
        Base64.getEncoder().encodeToString(cipher(text.toByteArray(StandardCharsets.UTF_8)))

    fun b64Decipher(b64Text: String): String =
        String(decipher(Base64.getDecoder().decode(b64Text)), StandardCharsets.UTF_8)

    private fun key(): SecretKeySpec = secret ?: throw IllegalStateException("No session key yet: the handshake did not run")

    /** HKDF-SHA256 (RFC 5869) for a 32-byte key: one block of expansion. */
    private fun hkdf(ikm: ByteArray, salt: ByteArray): ByteArray {
        fun hmac(key: ByteArray, data: ByteArray) =
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)
        val prk = hmac(salt, ikm)
        return hmac(prk, INFO.toByteArray(StandardCharsets.UTF_8) + byteArrayOf(1))
    }
}
