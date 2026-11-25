package br.com.wdc.framework.cube.remote.bridge

import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The session's cipher in the browser: the handshake's signature, and a new IV for every value. */
class DataSecurityTest {

    /** The host's development key (`RemoteAppSecurity` without keys set). */
    private val hostKey = "1ekh:3n88eu224huxfvj7lndkkf4n2vye4lus611fecnoc57qod2m7d"

    private suspend fun session() = DataSecurity().apply {
        updateSecurityKey(hostKey)
        updateSecretWithRandomPassword()
    }

    @Test
    fun theSignature_isThePasswordForTheHost_andTheSalt() = runTest {
        val parts = session().signature!!.split('.')
        assertEquals(2, parts.size, "no IV in the handshake")
        assertTrue(parts[0].all { it.isLetterOrDigit() }, "base 36")
        assertEquals(16, Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(parts[1]).size)
    }

    @Test
    fun eachValue_hasItsOwnIV_andDeciphers() = runTest {
        val security = session()
        val first = security.b64Cipher("fulano")
        val second = security.b64Cipher("fulano")

        assertNotEquals(first, second)
        assertNotEquals(Base64.decode(first).copyOf(12).toList(), Base64.decode(second).copyOf(12).toList())
        assertEquals("fulano", security.b64Decipher(first))
        assertEquals("fulano", security.b64Decipher(second))
    }

    @Test
    fun aChangedByte_isRefused() = runTest {
        val security = session()
        val bytes = Base64.decode(security.b64Cipher("fulano"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertFails { security.b64Decipher(Base64.encode(bytes)) }
    }
}
