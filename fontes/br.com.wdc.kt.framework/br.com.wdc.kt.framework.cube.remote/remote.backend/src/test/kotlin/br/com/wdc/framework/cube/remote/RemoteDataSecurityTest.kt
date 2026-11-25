package br.com.wdc.framework.cube.remote

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The session's cipher: the ECDH handshake with a client, and an IV of its own for every value. */
class RemoteDataSecurityTest {

    private val random = SecureRandom()

    /** The client's side, as the browser bridges do it with Web Crypto: an ephemeral P-256 pair, then HKDF. */
    private class Client(private val sessionId: String) {
        private val pair: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()
        val publicKey: String = Base64.getEncoder().encodeToString(pair.public.encoded)
        lateinit var key: SecretKeySpec

        fun agree(serverKey: String) {
            val serverPublic = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(serverKey)))
            val shared = KeyAgreement.getInstance("ECDH").run {
                init(pair.private)
                doPhase(serverPublic, true)
                generateSecret()
            }
            fun hmac(k: ByteArray, d: ByteArray) = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(k, "HmacSHA256")) }.doFinal(d)
            val prk = hmac(sessionId.toByteArray(), shared)
            key = SecretKeySpec(hmac(prk, RemoteDataSecurity.INFO.toByteArray() + byteArrayOf(1)), "AES")
        }

        fun cipher(text: String, iv: ByteArray): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            return Base64.getEncoder().encodeToString(iv + cipher.doFinal(text.toByteArray()))
        }

        fun decipher(b64: String): String {
            val data = Base64.getDecoder().decode(b64)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12))
            return String(cipher.doFinal(data, 12, data.size - 12))
        }
    }

    /** A session and a client that ran the handshake. */
    private fun pair(sessionId: String = "session-1"): Pair<RemoteDataSecurity, Client> {
        val host = RemoteDataSecurity(sessionId)
        val client = Client(sessionId)
        client.agree(host.handshake(client.publicKey))
        return host to client
    }

    @Test
    fun afterTheHandshake_bothSidesHoldTheSameKey() {
        val (host, client) = pair()
        val iv = ByteArray(RemoteDataSecurity.IV_SIZE).also(random::nextBytes)
        assertEquals("fulano", host.b64Decipher(client.cipher("fulano", iv)))
        assertEquals("fulano", client.decipher(host.b64Cipher("fulano")))
    }

    @Test
    fun theSameValueTwice_isCipheredDifferently_andBothDecipher() {
        val (host, _) = pair()
        val first = host.b64Cipher("the same password")
        val second = host.b64Cipher("the same password")

        assertNotEquals(first, second, "a new IV for each value")
        assertEquals("the same password", host.b64Decipher(first))
        assertEquals("the same password", host.b64Decipher(second))
    }

    @Test
    fun eachHandshake_makesANewKey_soAnOldSessionCannotBeOpened() {
        val (host, client) = pair()
        val old = client.cipher("fulano", ByteArray(RemoteDataSecurity.IV_SIZE).also(random::nextBytes))

        val reconnected = Client("session-1")
        reconnected.agree(host.handshake(reconnected.publicKey))

        assertFails { host.b64Decipher(old) }
    }

    @Test
    fun theSessionId_isPartOfTheKey() {
        val host = RemoteDataSecurity("session-1")
        val client = Client("session-2")
        client.agree(host.handshake(client.publicKey))
        assertFails { host.b64Decipher(client.cipher("fulano", ByteArray(12).also(random::nextBytes))) }
    }

    @Test
    fun aChangedByte_isRefused() {
        val (host, _) = pair()
        val bytes = Base64.getDecoder().decode(host.b64Cipher("fulano"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertFails { host.b64Decipher(Base64.getEncoder().encodeToString(bytes)) }
    }

    @Test
    fun aKeyOnAnotherCurve_isRefused() {
        val p384 = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp384r1")) }.generateKeyPair()
        val host = RemoteDataSecurity("session-1")
        assertFails { host.handshake(Base64.getEncoder().encodeToString(p384.public.encoded)) }
        assertFalse(host.ready)
    }

    @Test
    fun withoutTheHandshake_nothingIsCiphered() {
        assertFails { RemoteDataSecurity("session-1").b64Cipher("fulano") }
    }

    @Test
    fun sessionIds_andAddresses_areSignedByTheHostAlone() {
        val id = RemoteAppSecurity.newSessionId()
        assertTrue(RemoteAppSecurity.isSessionId(id))
        assertFalse(RemoteAppSecurity.isSessionId(id.substringBefore('.') + ".forged"))
        assertFalse(RemoteAppSecurity.isSessionId("no-signature"))

        val signature = RemoteAppSecurity.sign("home?pr-id=1")
        assertTrue(RemoteAppSecurity.verify("home?pr-id=1", signature))
        assertFalse(RemoteAppSecurity.verify("home?pr-id=2", signature))
    }
}
