package br.com.wdc.framework.cube.remote.bridge

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.await
import kotlinx.coroutines.withTimeout
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import kotlin.io.encoding.Base64
import kotlin.js.Promise

/**
 * The session's cipher, the client side of the host's `RemoteDataSecurity`, on top of TLS.
 *
 * **The key.** Each connection agrees on its own, with the browser's Web Crypto: the client makes an ephemeral ECDH
 * P-256 pair and sends its public key ([startHandshake], `clientKey`); the host answers with its own (`serverKey`,
 * [completeHandshake]); both derive the AES-256-GCM key from the shared secret with HKDF-SHA256 — the session id as
 * salt, [INFO] as info. No long-lived key: a recorded session cannot be opened later (forward secrecy). Public keys
 * travel as Base64 SubjectPublicKeyInfo.
 *
 * **The values.** Each one gets an IV of its own, random, sent in front of it: `Base64(IV ‖ ciphertext and tag)`.
 */
class DataSecurity {

    companion object {
        /** The HKDF info: what the derived key is for, and the version of this scheme (the host uses the same). */
        const val INFO = "wdc-cube-remote/aes-256-gcm/v1"

        private const val IV_SIZE = 12

        /** How long [b64Cipher] waits for a handshake to finish. */
        private const val KEY_WAIT_MS = 15_000L

        private val subtle: dynamic get() = js("globalThis.crypto.subtle")
    }

    private var privateKey: dynamic = null

    /** The key of the current handshake; replaced by each new one (a reconnection). */
    private var key = CompletableDeferred<dynamic>()

    /** Starts a handshake: a new P-256 pair; returns the public key to send as `clientKey`. */
    suspend fun startHandshake(): String {
        if (key.isCompleted) key = CompletableDeferred()
        val algorithm: dynamic = js("({ name: 'ECDH', namedCurve: 'P-256' })")
        val pair: dynamic = subtle.generateKey(algorithm, false, arrayOf("deriveBits")).unsafeCast<Promise<dynamic>>().await()
        privateKey = pair.privateKey
        val spki = subtle.exportKey("spki", pair.publicKey).unsafeCast<Promise<ArrayBuffer>>().await()
        return Base64.encode(spki.toBytes())
    }

    /** Ends the handshake with the host's public key ([serverKey]) for the session [sessionId]. */
    suspend fun completeHandshake(serverKey: String, sessionId: String) {
        val algorithm: dynamic = js("({ name: 'ECDH', namedCurve: 'P-256' })")
        val serverPublic = subtle.importKey("spki", Base64.decode(serverKey).toUint8(), algorithm, false, arrayOf<String>())
            .unsafeCast<Promise<dynamic>>().await()

        val ecdh: dynamic = js("({ name: 'ECDH' })")
        ecdh.public = serverPublic
        val shared = subtle.deriveBits(ecdh, privateKey ?: error("No handshake started"), 256)
            .unsafeCast<Promise<ArrayBuffer>>().await()

        val hkdfKey = subtle.importKey("raw", shared, "HKDF", false, arrayOf("deriveKey")).unsafeCast<Promise<dynamic>>().await()
        val hkdf: dynamic = js("({ name: 'HKDF', hash: 'SHA-256' })")
        hkdf.salt = sessionId.encodeToByteArray().toUint8()
        hkdf.info = INFO.encodeToByteArray().toUint8()
        val aes: dynamic = js("({ name: 'AES-GCM', length: 256 })")
        val derived = subtle.deriveKey(hkdf, hkdfKey, aes, false, arrayOf("encrypt", "decrypt"))
            .unsafeCast<Promise<dynamic>>().await()
        privateKey = null
        if (key.isCompleted) key = CompletableDeferred()
        key.complete(derived)
    }

    /** Ciphers [text] with a new random IV, in front of the result; Base64. Waits for the handshake if one is running. */
    suspend fun b64Cipher(text: String): String {
        val iv = randomBytes(IV_SIZE)
        val ciphered = subtle.encrypt(gcm(iv), sessionKey(), text.encodeToByteArray().toUint8())
            .unsafeCast<Promise<ArrayBuffer>>().await()
        return Base64.encode(iv + ciphered.toBytes())
    }

    /** Deciphers what [b64Cipher] (or the host) made. */
    suspend fun b64Decipher(b64Text: String): String {
        val data = Base64.decode(b64Text)
        val plain = subtle.decrypt(gcm(data.copyOfRange(0, IV_SIZE)), sessionKey(), data.copyOfRange(IV_SIZE, data.size).toUint8())
            .unsafeCast<Promise<ArrayBuffer>>().await()
        return plain.toBytes().decodeToString()
    }

    private suspend fun sessionKey(): dynamic = withTimeout(KEY_WAIT_MS) { key.await() }

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
