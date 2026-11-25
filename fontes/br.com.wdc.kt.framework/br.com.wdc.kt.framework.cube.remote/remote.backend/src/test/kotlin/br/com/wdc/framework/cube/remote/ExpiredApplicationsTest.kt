package br.com.wdc.framework.cube.remote

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.cube.CubeApplication
import br.com.wdc.framework.cube.Scope
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The smallest remote application: no places, an empty root scope. */
internal class TestRemoteApplication(id: String) : CubeApplication(emptyList()), RemoteApplication {

    class PageScope : Scope()

    val page = PageScope()

    override val remote = RemoteApplicationSupport(id, this, mapOf(PageScope::class to "0123456789ab"), content = page)

    val alerts = ArrayList<String>()

    override fun alertUnexpectedError(logger: Log, message: String, e: Throwable) {
        alerts += message
        remote.browser.alertUnexpectedError(message)
    }

    override fun updateHistory() = remote.updateHistory()

    override fun release() {
        remote.release()
        super.release()
    }
}

/** A remote application whose client went away is released once past its life; a connected one gets more life. */
class ExpiredApplicationsTest {

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
    fun theRegistry_makesOneApplicationPerSession() {
        val app = newApp("one-per-session")
        assertSame(app, registry.getOrCreate("one-per-session", emptyMap()))
        assertEquals(1, registry.activeSessionCount)
    }

    @Test
    fun pastItsLife_withoutASocket_theApplicationIsReleased() {
        val app = newApp("expired-without-socket")
        app.remote.expireMoment = System.currentTimeMillis() - 1

        registry.removeExpireds()

        assertNull(registry.get("expired-without-socket"))
    }

    @Test
    fun pastItsLife_withASocket_itGetsMoreLife() {
        val app = newApp("expired-with-socket")
        app.remote.wsSession = object : WebSocketConnection {
            override fun sendText(text: String) {}
        }
        app.remote.expireMoment = System.currentTimeMillis() - 1

        registry.removeExpireds()

        assertNotNull(registry.get("expired-with-socket"))
        assertTrue(app.remote.expireMoment > System.currentTimeMillis())
    }

    @Test
    fun beforeItsLifeEnds_nothingHappens() {
        newApp("still-alive")
        registry.removeExpireds()
        assertNotNull(registry.get("still-alive"))
    }
}
