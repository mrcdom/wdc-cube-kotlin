package br.com.wdc.store.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.store.view.react.skeleton.spi.WebSocketConnection
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A remote application whose client went away is released once past its life; a connected one gets more life. */
class ExpiredApplicationsTest {

    init {
        JsonOutputFactory.installCommon()
        JsonInputFactory.installCommon()
    }

    private val ids = mutableListOf<String>()

    @AfterTest
    fun release() = ids.forEach { ReactRootPresenter.get(it)?.release() }

    private fun newApp(id: String) = ReactRootPresenter.getOrCreate(id, emptyMap()).also { ids += id }

    @Test
    fun pastItsLife_withoutASocket_theApplicationIsReleased() {
        val app = newApp("expired-without-socket")
        app.expireMoment = System.currentTimeMillis() - 1

        ReactRootPresenter.removeExpireds()

        assertNull(ReactRootPresenter.get("expired-without-socket"))
    }

    @Test
    fun pastItsLife_withASocket_itGetsMoreLife() {
        val app = newApp("expired-with-socket")
        app.wsSession = object : WebSocketConnection {
            override fun sendText(text: String) {}
        }
        app.expireMoment = System.currentTimeMillis() - 1

        ReactRootPresenter.removeExpireds()

        assertNotNull(ReactRootPresenter.get("expired-with-socket"))
        assertTrue(app.expireMoment > System.currentTimeMillis())
    }

    @Test
    fun beforeItsLifeEnds_nothingHappens() {
        newApp("still-alive")
        ReactRootPresenter.removeExpireds()
        assertNotNull(ReactRootPresenter.get("still-alive"))
    }
}
