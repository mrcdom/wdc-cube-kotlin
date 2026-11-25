package br.com.wdc.framework.cube.remote

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import com.google.gson.JsonParser
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A client message and its answer, through the session alone (no socket server). */
class RemoteProtocolTest {

    init {
        JsonOutputFactory.installCommon()
        JsonInputFactory.installCommon()
    }

    private val registry = RemoteApplicationRegistry(::TestRemoteApplication)
    private val sent = ArrayList<String>()
    private val app = registry.getOrCreate("protocol-test", emptyMap()) as TestRemoteApplication

    init {
        app.remote.wsSession = object : WebSocketConnection {
            override fun sendText(text: String) {
                sent += text
            }
        }
    }

    @AfterTest
    fun release() = app.release()

    private fun send(request: Map<String, Any?>) = app.remote.sendResponse(request)

    private fun lastAnswer() = JsonParser.parseString(sent.last()).asJsonObject

    @Test
    fun theFirstAnswer_bringsTheBrowser_theRootScope_andASignedAddress() {
        send(mapOf("requestId" to 1.0))

        val answer = lastAnswer()
        assertEquals(1, answer["requestId"].asInt)
        val scopes = answer["scopes"].asJsonArray.map { it.asJsonObject }
        val browser = scopes.first { it["id"].asString == RemoteBrowserPresenter.VSID }
        assertEquals("0123456789ab:1", browser["contentViewId"].asString)
        assertTrue(scopes.any { it["id"].asString == "0123456789ab:1" }, "the page goes in the same message")
        assertTrue(answer["uri"].asString.contains("sign="), "the address is signed: ${answer["uri"]}")
    }

    @Test
    fun anEvent_runsTheActionBoundToItsCode_andAnAnswerFollows() {
        send(mapOf("requestId" to 1.0))
        app.alertUnexpectedError(Log.getLogger("test"), "Buying a product", IllegalStateException())

        // 1 on the browser's view: the alert's OK
        send(mapOf("requestId" to 2.0, "event" to listOf("${RemoteBrowserPresenter.VSID}:1")))

        assertNull(app.remote.browser.scope.alertMessage)
        assertEquals(2, lastAnswer()["requestId"].asInt)
    }

    @Test
    fun theAlert_showsOnlyWhatFailed() {
        send(mapOf("requestId" to 1.0))
        app.alertUnexpectedError(Log.getLogger("test"), "Buying a product: connection refused", IllegalStateException())

        send(mapOf("requestId" to 2.0, "ping" to true))

        val browser = lastAnswer()["scopes"].asJsonArray.map { it.asJsonObject }
            .first { it["id"].asString == RemoteBrowserPresenter.VSID }
        assertEquals("""{"id":-1,"args":["Buying a product: connection refused"]}""", browser["alertMessage"].toString())
    }

    @Test
    fun anAddressWithAWrongSignature_isNotFollowed_andTheClientIsCorrected() = kotlinx.coroutines.runBlocking {
        send(mapOf("requestId" to 1.0))
        val signed = app.remote.uri!!

        app.remote.safeGo("somewhere?sign=forged")
        send(mapOf("requestId" to 2.0, "ping" to true))

        assertEquals(signed, lastAnswer()["uri"].asString)
    }

    @Test
    fun aClientKey_isAnsweredWithTheHostsKey_onceAndOnlyOnce() {
        val clientPair = java.security.KeyPairGenerator.getInstance("EC")
            .apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val clientKey = java.util.Base64.getEncoder().encodeToString(clientPair.public.encoded)

        send(mapOf("requestId" to 1.0, "ping" to true, "clientKey" to clientKey))
        assertTrue(lastAnswer().has("serverKey"), "the host answers the handshake")
        assertTrue(app.remote.dataSecurity.ready)

        send(mapOf("requestId" to 2.0, "ping" to true))
        assertTrue(!lastAnswer().has("serverKey"), "only the answer to the handshake carries it")
    }
}
