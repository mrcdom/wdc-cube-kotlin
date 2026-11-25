package br.com.wdc.framework.cube.remote.bridge

import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals

/**
 * The session's cipher in the browser, with the real Web Crypto: two sides that ran the ECDH handshake with each other
 * hold the same key (the host does the same derivation; its own test checks it against a JCA client).
 */
class DataSecurityTest {

    private suspend fun handshake(sessionId: String = "session-1"): Pair<DataSecurity, DataSecurity> {
        val client = DataSecurity()
        val host = DataSecurity()
        val clientKey = client.startHandshake()
        val hostKey = host.startHandshake()
        client.completeHandshake(hostKey, sessionId)
        host.completeHandshake(clientKey, sessionId)
        return client to host
    }

    @Test
    fun thePublicKey_isAP256SubjectPublicKeyInfo() = runTest {
        // DER of an uncompressed P-256 point in SPKI: 91 bytes
        assertEquals(91, Base64.decode(DataSecurity().startHandshake()).size)
    }

    @Test
    fun afterTheHandshake_bothSidesHoldTheSameKey_andEachValueHasItsOwnIV() = runTest {
        val (client, host) = handshake()
        val first = client.b64Cipher("fulano")
        val second = client.b64Cipher("fulano")

        assertNotEquals(first, second)
        assertNotEquals(Base64.decode(first).copyOf(12).toList(), Base64.decode(second).copyOf(12).toList())
        assertEquals("fulano", host.b64Decipher(first))
        assertEquals("fulano", host.b64Decipher(second))
    }

    @Test
    fun theSessionId_isPartOfTheKey() = runTest {
        val client = DataSecurity()
        val host = DataSecurity()
        val clientKey = client.startHandshake()
        val hostKey = host.startHandshake()
        client.completeHandshake(hostKey, "session-1")
        host.completeHandshake(clientKey, "session-2")
        val ciphered = client.b64Cipher("fulano")
        assertFails { host.b64Decipher(ciphered) }
    }

    @Test
    fun aChangedByte_isRefused() = runTest {
        val (client, host) = handshake()
        val bytes = Base64.decode(client.b64Cipher("fulano"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertFails { host.b64Decipher(Base64.encode(bytes)) }
    }
}
