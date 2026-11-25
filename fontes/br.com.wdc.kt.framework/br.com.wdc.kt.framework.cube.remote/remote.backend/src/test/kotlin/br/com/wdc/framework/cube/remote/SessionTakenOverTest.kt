package br.com.wdc.framework.cube.remote

import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * With several instances: the registry says which sessions it made ([RemoteApplicationRegistry.onCreated]), and lets go
 * of one whose client went on elsewhere ([RemoteApplicationRegistry.releaseIdle]).
 */
class SessionTakenOverTest {

    init {
        JsonOutputFactory.installCommon()
        JsonInputFactory.installCommon()
    }

    private val registry = RemoteApplicationRegistry(::TestRemoteApplication)

    private val ids = mutableListOf<String>()

    @AfterTest
    fun release() = ids.forEach { registry.get(it)?.release() }

    private fun newApp(id: String) = registry.getOrCreate(id, emptyMap()).also { ids += id }

    @Test
    fun aSessionMadeHere_isTold_once() {
        val created = ArrayList<String>()
        val listening = registry.onCreated { created += it }

        newApp("told-once")
        newApp("told-once")
        listening.close()
        newApp("not-told")

        assertEquals(listOf("told-once"), created)
    }

    @Test
    fun aSessionWithoutASocket_isReleased() {
        newApp("idle")
        assertTrue(registry.releaseIdle("idle"))
        assertNull(registry.get("idle"))
    }

    @Test
    fun aSessionWithASocket_isKept() {
        val app = newApp("connected")
        app.remote.wsSession = object : WebSocketConnection {
            override fun sendText(text: String) {}
        }
        assertFalse(registry.releaseIdle("connected"))
        assertNotNull(registry.get("connected"))
    }

    @Test
    fun anUnknownSession_isNothingToRelease() {
        assertFalse(registry.releaseIdle("unknown"))
    }
}
