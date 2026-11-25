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

    /** A new P-256 public key, as a client sends it (Base64 SubjectPublicKeyInfo). */
    private fun newClientKey(): String {
        val pair = java.security.KeyPairGenerator.getInstance("EC")
            .apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        return java.util.Base64.getEncoder().encodeToString(pair.public.encoded)
    }

    /** The handshake every client starts with: its key and the version it speaks. */
    private fun hello(protocol: Any? = RemoteProtocol.VERSION) {
        val request = mutableMapOf<String, Any?>("requestId" to 0.0, "ping" to true, "clientKey" to newClientKey())
        if (protocol != null) request["protocol"] = protocol
        send(request)
    }

    @AfterTest
    fun release() = app.release()

    private fun send(request: Map<String, Any?>) = app.remote.sendResponse(request)

    private fun lastAnswer() = JsonParser.parseString(sent.last()).asJsonObject

    @Test
    fun theAnswerToTheHandshake_bringsTheBrowser_theRootScope_andASignedAddress() {
        hello()

        val answer = lastAnswer()
        assertEquals(0, answer["requestId"].asInt)
        val scopes = answer["scopes"].asJsonArray.map { it.asJsonObject }
        val browser = scopes.first { it["id"].asString == RemoteBrowserPresenter.VSID }
        assertEquals("0123456789ab:1", browser["contentViewId"].asString)
        assertTrue(scopes.any { it["id"].asString == "0123456789ab:1" }, "the page goes in the same message")
        assertTrue(answer["uri"].asString.contains("sign="), "the address is signed: ${answer["uri"]}")
    }

    @Test
    fun anEvent_runsTheActionBoundToItsCode_andAnAnswerFollows() {
        hello()
        send(mapOf("requestId" to 1.0))
        app.alertUnexpectedError(Log.getLogger("test"), "Buying a product", IllegalStateException())

        // 1 on the browser's view: the alert's OK
        send(mapOf("requestId" to 2.0, "event" to listOf("${RemoteBrowserPresenter.VSID}:1")))

        assertNull(app.remote.browser.scope.alertMessage)
        assertEquals(2, lastAnswer()["requestId"].asInt)
    }

    @Test
    fun theAlert_showsOnlyWhatFailed() {
        hello()
        send(mapOf("requestId" to 1.0))
        app.alertUnexpectedError(Log.getLogger("test"), "Buying a product: connection refused", IllegalStateException())

        send(mapOf("requestId" to 2.0, "ping" to true))

        val browser = lastAnswer()["scopes"].asJsonArray.map { it.asJsonObject }
            .first { it["id"].asString == RemoteBrowserPresenter.VSID }
        assertEquals("""{"id":-1,"args":["Buying a product: connection refused"]}""", browser["alertMessage"].toString())
    }

    @Test
    fun anAddressWithAWrongSignature_isNotFollowed_andTheClientIsCorrected() = kotlinx.coroutines.runBlocking {
        hello()
        send(mapOf("requestId" to 1.0))
        val signed = app.remote.uri!!

        app.remote.safeGo("somewhere?sign=forged")
        send(mapOf("requestId" to 2.0, "ping" to true))

        assertEquals(signed, lastAnswer()["uri"].asString)
    }

    @Test
    fun aClientKey_isAnsweredWithTheHostsKey_onceAndOnlyOnce() {
        send(mapOf("requestId" to 1.0, "ping" to true, "clientKey" to newClientKey(), "protocol" to RemoteProtocol.VERSION))
        assertTrue(lastAnswer().has("serverKey"), "the host answers the handshake")
        assertEquals(RemoteProtocol.VERSION, lastAnswer()["protocol"].asInt, "with the version it speaks")
        assertTrue(app.remote.dataSecurity.ready)

        send(mapOf("requestId" to 2.0, "ping" to true))
        assertTrue(!lastAnswer().has("serverKey"), "only the answer to the handshake carries it")
        assertTrue(!lastAnswer().has("protocol"))
    }

    // :: The protocol's version

    @Test
    fun aHandshakeOfAnotherVersion_isRefused_withTheVersionsTheHostSpeaks() {
        hello(protocol = RemoteProtocol.VERSION + 1)
        assertEquals(RemoteProtocol.UNSUPPORTED, lastAnswer()["error"].asString)
        assertEquals(listOf(RemoteProtocol.VERSION), lastAnswer()["protocols"].asJsonArray.map { it.asInt })
        assertTrue(!lastAnswer().has("serverKey"), "no key for a client the host cannot talk to")
        assertTrue(!app.remote.dataSecurity.ready)
    }

    @Test
    fun aHandshakeWithoutAVersion_isRefused() {
        hello(protocol = null)
        assertEquals(RemoteProtocol.UNSUPPORTED, lastAnswer()["error"].asString)
    }

    @Test
    fun aMessageBeforeAnAcceptedHandshake_isRefused_andNothingRuns() {
        send(mapOf("requestId" to 1.0, "event" to listOf("${RemoteBrowserPresenter.VSID}:2")))
        assertEquals(RemoteProtocol.UNSUPPORTED, lastAnswer()["error"].asString)
        assertTrue(!lastAnswer().has("scopes"), "nothing was read")

        // a refused client stays refused, even with the right version later only through a new handshake
        hello()
        send(mapOf("requestId" to 2.0, "ping" to true))
        assertTrue(!lastAnswer().has("error"))
    }
}
