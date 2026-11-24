package br.com.wdc.shopping.view.react.skeleton.viewimpl

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

    private fun browserState(): String {
        val out = StringWriter()
        app.browserView
            .writeState(JsonExtensibleObjectOutput(JsonWriter(out)))
        return out.toString()
    }

    @Test
    fun theClientGetsOnlyWhatFailed_neverTheStackTrace() {
        app.alertUnexpectedError(log, "Buying a product", IllegalStateException("connection refused"))
        assertEquals(
            """{"id":"${BrowserReactViewImpl.VSID}","alertMessage":{"id":-1,"args":["Buying a product: connection refused"]}}""",
            browserState(),
        )
    }

    @Test
    fun withoutACauseMessage_onlyWhatFailed() {
        app.alertUnexpectedError(log, "Buying a product", IllegalStateException())
        assertEquals("""{"id":"${BrowserReactViewImpl.VSID}","alertMessage":{"id":-1,"args":["Buying a product"]}}""", browserState())
    }
}
