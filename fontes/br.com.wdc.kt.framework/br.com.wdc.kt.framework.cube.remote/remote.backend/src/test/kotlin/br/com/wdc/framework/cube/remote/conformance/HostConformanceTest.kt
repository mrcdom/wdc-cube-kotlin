package br.com.wdc.framework.cube.remote.conformance

import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.framework.cube.AbstractPresenter
import br.com.wdc.framework.cube.Action
import br.com.wdc.framework.cube.CubeApplication
import br.com.wdc.framework.cube.FormParam
import br.com.wdc.framework.cube.Scope
import br.com.wdc.framework.cube.remote.RemoteApplication
import br.com.wdc.framework.cube.remote.RemoteApplicationRegistry
import br.com.wdc.framework.cube.remote.RemoteApplicationSupport
import br.com.wdc.framework.cube.remote.WebSocketConnection
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.assertTrue
import kotlin.test.fail

/** The conformance application (`conformance/README.md`): a counter, and rows that can be added and removed. */
internal class ConformanceApplication(id: String) : CubeApplication(emptyList()), RemoteApplication {

    class CounterScope : Scope() {
        var count: Int by field(0)
        var rows: List<RowScope> by scopes()
        var onIncrement: Action = Action.NOOP
        var onAddRow: br.com.wdc.framework.cube.ActionOf<String?> = br.com.wdc.framework.cube.ActionOf.noop()
    }

    class RowScope : Scope() {
        var label: String? by field(null)
        var onRemove: Action = Action.NOOP
    }

    private class CounterPresenter(app: ConformanceApplication) : AbstractPresenter<ConformanceApplication>(app) {
        val scope = bind(CounterScope())

        init {
            scope.onIncrement = action(1) { onIncrement() }
            scope.onAddRow = actionOf(2, FormParam.string("p.label")) { onAddRow(it) }
        }

        private fun onIncrement() {
            scope.count++
        }

        private fun onAddRow(label: String?) {
            val row = RowScope()
            row.label = label
            row.onRemove = action(1, target = row) { onRemove(row) }
            scope.rows = scope.rows + row
        }

        private fun onRemove(row: RowScope) {
            scope.rows = scope.rows - row
        }
    }

    private val counter = CounterPresenter(this)

    override val remote = RemoteApplicationSupport(
        id,
        this,
        mapOf(CounterScope::class to "c0ffee000001", RowScope::class to "c0ffee000002"),
        content = counter.scope,
    )

    override fun updateHistory() = remote.updateHistory()

    override fun release() {
        remote.release()
        super.release()
    }
}

/** Runs every host fixture of `conformance/host/` against this host and the conformance application. */
class HostConformanceTest {

    init {
        JsonOutputFactory.installCommon()
        JsonInputFactory.installCommon()
    }

    @TestFactory
    fun hostFixtures(): List<DynamicTest> {
        val dir = File(System.getProperty("conformance.dir") ?: fail("conformance.dir is not set"), "host")
        val files = dir.listFiles { f -> f.name.endsWith(".json") }?.sortedBy { it.name }.orEmpty()
        assertTrue(files.isNotEmpty(), "no host fixtures in $dir")
        return files.map { file -> DynamicTest.dynamicTest(file.name) { run(JsonParser.parseString(file.readText()).asJsonObject) } }
    }

    private fun run(fixture: JsonObject) {
        val registry = RemoteApplicationRegistry(::ConformanceApplication)
        val app = registry.getOrCreate("conformance-${System.nanoTime()}", emptyMap()) as ConformanceApplication
        val sent = ArrayList<String>()
        app.remote.wsSession = object : WebSocketConnection {
            override fun sendText(text: String) {
                sent += text
            }
        }
        val matcher = FixtureMatcher()
        try {
            fixture["steps"].asJsonArray.forEachIndexed { index, element ->
                val step = element.asJsonObject
                val before = sent.size
                app.remote.sendResponse(toMap(withClientKey(matcher.substitute(step["send"]))))
                val answers = sent.drop(before)
                try {
                    when {
                        step.has("expect") -> {
                            if (answers.size != 1) fail("expected one answer, got ${answers.size}: $answers")
                            matcher.match(step["expect"], JsonParser.parseString(answers.single()))
                        }
                        step.has("expectNothing") -> if (answers.isNotEmpty()) fail("expected no answer, got $answers")
                    }
                } catch (e: AssertionError) {
                    throw AssertionError("step ${index + 1}: ${e.message}", e)
                }
            }
        } finally {
            app.release()
        }
    }

    /** `"$clientKey"`: a new P-256 public key, as a client makes one for its handshake. */
    private fun withClientKey(message: JsonElement): JsonElement {
        val obj = message.asJsonObject
        if (obj["clientKey"]?.asString == "\$clientKey") {
            val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
            obj.addProperty("clientKey", Base64.getEncoder().encodeToString(pair.public.encoded))
        }
        return obj
    }

    /** The message as the WebSocket route gives it to the session: Gson's maps, numbers as doubles. */
    private fun toMap(message: JsonElement): Map<String, Any?> =
        Gson().fromJson(message, object : TypeToken<Map<String, Any?>>() {}.type)
}
