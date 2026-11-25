package br.com.wdc.store.backend

import br.com.wdc.framework.cube.remote.RemoteAppSecurity
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Instances with the same JWT secret sign remote session ids with the same key; another secret, another key. */
class RemoteSigningKeyTest {

    @AfterTest
    fun anotherKey() = RemoteAppSecurity.useKey(RemoteSigningKey.from("after-the-test-${System.nanoTime()}"))

    @Test
    fun theSameSecret_givesTheSameKey_andAnotherSecretAnother() {
        val key = RemoteSigningKey.from("a-long-random-secret")
        assertEquals(32, key.size)
        assertContentEquals(key, RemoteSigningKey.from("a-long-random-secret"))
        assertFalse(key.contentEquals(RemoteSigningKey.from("another-long-random-secret")))
    }

    @Test
    fun aSessionIdMadeByOneInstance_isAcceptedByAnotherOfTheSameConfiguration_only() {
        // instance A makes the id the page carries
        RemoteAppSecurity.useKey(RemoteSigningKey.from("the-deployment-secret"))
        val id = RemoteAppSecurity.newSessionId()

        // instance B, configured with another secret, refuses it
        RemoteAppSecurity.useKey(RemoteSigningKey.from("another-deployment-secret"))
        assertFalse(RemoteAppSecurity.isSessionId(id))

        // instance B, with the same configuration as A, accepts it
        RemoteAppSecurity.useKey(RemoteSigningKey.from("the-deployment-secret"))
        assertTrue(RemoteAppSecurity.isSessionId(id))
    }
}
