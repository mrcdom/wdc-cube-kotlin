package br.com.wdc.framework.cube.remote.bridge

import br.com.wdc.framework.commons.security.RSA
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import kotlin.io.encoding.Base64
import kotlin.js.Promise

/**
 * The session's cipher, the client side of the host's `RemoteDataSecurity`: AES-256-GCM with a key derived by PBKDF2
 * (SHA-256, 250,000 iterations) from a random password, which goes to the host ciphered with its RSA key — the
 * signature, `<password ciphered with RSA, base 36>.<salt, Base64 URL>`. Every value gets an IV of its own, random,
 * sent in front of it: `Base64(IV ‖ ciphertext and tag)`. Uses the browser's Web Crypto.
 */
class DataSecurity {

    private companion object {
        const val IV_SIZE = 12
        const val ITERATIONS = 250_000
        val subtle: dynamic get() = js("globalThis.crypto.subtle")
    }

    private var rsa: RSA? = null
    private var key: dynamic = null

    /** The handshake's signature, once [updateSecretWithRandomPassword] ran. */
    var signature: String? = null
        private set

    /** The host's public key, from the `app_skey` cookie: `<exponent, base 36>:<modulus, base 36>`. */
    fun updateSecurityKey(appSKey: String) {
        val (exponent, modulus) = appSKey.split(':')
        rsa = RSA(BigInteger.parseString(exponent, 36), BigInteger.ZERO, BigInteger.parseString(modulus, 36))
    }

    /** Makes a random password, derives the key from it, and ciphers it for the host. */
    suspend fun updateSecretWithRandomPassword() {
        val password = Base64.UrlSafe.encode(randomBytes(12)).encodeToByteArray()
        val salt = randomBytes(16)

        val material = subtle.importKey("raw", password.toUint8(), "PBKDF2", false, arrayOf("deriveKey"))
            .unsafeCast<Promise<dynamic>>().await()
        val params: dynamic = js("({ name: 'PBKDF2', hash: 'SHA-256' })")
        params.salt = salt.toUint8()
        params.iterations = ITERATIONS
        val aes: dynamic = js("({ name: 'AES-GCM', length: 256 })")
        key = subtle.deriveKey(params, material, aes, false, arrayOf("encrypt", "decrypt"))
            .unsafeCast<Promise<dynamic>>().await()

        val host = rsa ?: error("No host key: the page did not get the app_skey cookie")
        // the host reads the RSA message as the Base64 text of the password
        val message = BigInteger.fromByteArray(Base64.encode(password).encodeToByteArray(), Sign.POSITIVE)
        signature = "${host.encrypt(message).toString(36)}.${Base64.UrlSafe.encode(salt).trimEnd('=')}"
    }

    /** Ciphers [text] with a new random IV, in front of the result; Base64. */
    suspend fun b64Cipher(text: String): String {
        val iv = randomBytes(IV_SIZE)
        val ciphered = subtle.encrypt(gcm(iv), sessionKey(), text.encodeToByteArray().toUint8())
            .unsafeCast<Promise<ArrayBuffer>>().await()
        return Base64.encode(iv + ciphered.toBytes())
    }

    /** Deciphers what [b64Cipher] (or the host) made. */
    suspend fun b64Decipher(b64Text: String): String {
        val data = Base64.decode(b64Text)
        val iv = data.copyOfRange(0, IV_SIZE)
        val plain = subtle.decrypt(gcm(iv), sessionKey(), data.copyOfRange(IV_SIZE, data.size).toUint8())
            .unsafeCast<Promise<ArrayBuffer>>().await()
        return plain.toBytes().decodeToString()
    }

    private fun sessionKey(): dynamic = key ?: error("No session key yet: the handshake did not run")

    private fun gcm(iv: ByteArray): dynamic {
        val params: dynamic = js("({ name: 'AES-GCM' })")
        params.iv = iv.toUint8()
        return params
    }

    private fun randomBytes(size: Int): ByteArray {
        val array = Uint8Array(size)
        js("globalThis.crypto").getRandomValues(array)
        return Int8Array(array.buffer).unsafeCast<ByteArray>().copyOf()
    }

    private fun ByteArray.toUint8(): Uint8Array {
        val bytes = Int8Array(size)
        bytes.asDynamic().set(this)
        return Uint8Array(bytes.buffer)
    }

    private fun ArrayBuffer.toBytes(): ByteArray = Int8Array(this).unsafeCast<ByteArray>().copyOf()
}
