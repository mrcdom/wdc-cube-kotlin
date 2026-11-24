package br.com.wdc.store.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.gson.JsonExtensibleObjectOutput
import br.com.wdc.framework.commons.log.Log
import com.google.gson.stream.JsonWriter
import java.io.StringWriter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UnexpectedErrorAlertTest {

    private val app = ReactRootPresenter("unexpected-error-alert-test")
    private val log = Log.getLogger("UnexpectedErrorAlertTest")

    @AfterTest
    fun release() = app.release()

    private fun browserScope(): String {
        val out = StringWriter()
        app.browserView
            .writeScope(JsonExtensibleObjectOutput(JsonWriter(out)))
        return out.toString()
    }

    @Test
    fun theClientGetsOnlyWhatFailed_neverTheStackTrace() {
        app.alertUnexpectedError(log, "Buying a product", IllegalStateException("connection refused"))
        assertEquals(
            """{"id":"${BrowserPresenter.VSID}","alertMessage":{"id":-1,"args":["Buying a product: connection refused"]},"contentViewId":"f2d345c4a610:1"}""",
            browserScope(),
        )
    }

    @Test
    fun withoutACauseMessage_onlyWhatFailed() {
        app.alertUnexpectedError(log, "Buying a product", IllegalStateException())
        assertEquals("""{"id":"${BrowserPresenter.VSID}","alertMessage":{"id":-1,"args":["Buying a product"]},"contentViewId":"f2d345c4a610:1"}""", browserScope())
    }

    @Test
    fun theAlertsOk_dismissesIt_byItsEventCode() = kotlinx.coroutines.runBlocking {
        app.alertUnexpectedError(log, "Buying a product", IllegalStateException())
        app.browser.scope.dispatch(1, emptyMap())
        assertEquals("""{"id":"${BrowserPresenter.VSID}","contentViewId":"f2d345c4a610:1"}""", browserScope())
    }
}
