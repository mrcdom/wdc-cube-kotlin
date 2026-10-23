package br.com.wdc.framework.rest

import br.com.wdc.framework.rest.json.GsonJsonMapper
import br.com.wdc.framework.rest.json.JsonOptions
import br.com.wdc.framework.rest.testing.RestTestServer
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class RequestContextTest {

    companion object {
        val TIMEZONE = RequestKey<ZoneId>("timezone")
        val APP_VERSION = RequestKey<String>("appVersion")

        /** Camada "de baixo": não recebe nada, lê da requisição corrente. */
        fun clientZone(): ZoneId = RequestContext.currentOrNull()?.get(TIMEZONE) ?: ZoneOffset.UTC
    }

    /** Adaptador que depende da requisição: converte UTC para o fuso do cliente na escrita. */
    class ClientZoneAdapter : TypeAdapter<LocalDateTime>() {
        override fun write(out: JsonWriter, value: LocalDateTime?) {
            if (value == null) {
                out.nullValue()
                return
            }
            out.value(value.atOffset(ZoneOffset.UTC).atZoneSameInstant(clientZone()).toLocalDateTime().toString())
        }

        override fun read(input: JsonReader): LocalDateTime = LocalDateTime.parse(input.nextString())
    }

    data class Event(val at: LocalDateTime)

    private val events = CopyOnWriteArrayList<String>()

    private val api = RestApi {
        json = GsonJsonMapper(JsonOptions().apply { customize { it.registerTypeAdapter(LocalDateTime::class.java, ClientZoneAdapter()) } })
        before { r ->
            r[TIMEZONE] = r.header("X-Timezone")?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            r[APP_VERSION] = r.header("X-App-Version")
            events += "before ${r.path}"
        }
        before("/guarded/**") { r ->
            if (r.header("X-Tenant") == null) throw br.com.wdc.framework.domain.exception.InvalidRequestException("X-Tenant")
        }
        after { r, failure -> events += "after ${r.path} ${failure?.javaClass?.simpleName}" }

        get("/zone") { _ ->
            coroutineScope {
                val a = async(Dispatchers.Default) { clientZone().id }
                val b = withContext(Dispatchers.IO) {
                    delay(5)
                    RequestContext.current()[APP_VERSION]
                }
                mapOf("default" to a.await(), "io" to b)
            }
        }
        get("/event") { _ -> Event(LocalDateTime.of(2026, 1, 1, 12, 0)) }
        get("/guarded/x") { _ -> "ok" }
    }

    private val server = RestTestServer(api)

    @AfterTest
    fun stop() = server.close()

    @Test
    fun contextFollowsTheRequestThroughCoroutines() {
        val response = server.client().header("X-Timezone", "Asia/Tokyo").header("X-App-Version", "2.4.1").get("/zone")
        assertEquals("""{"default":"Asia/Tokyo","io":"2.4.1"}""", response.body)
        assertNull(RequestContext.currentOrNull(), "fora da requisição não há contexto")
    }

    @Test
    fun serializationSeesTheRequest() {
        assertEquals("""{"at":"2026-01-01T21:00"}""", server.client().header("X-Timezone", "Asia/Tokyo").get("/event").body)
        assertEquals("""{"at":"2026-01-01T12:00"}""", server.client().get("/event").body)
    }

    @Test
    fun filters_runAroundTheRoute_andCanRefuse() {
        events.clear()
        assertEquals(400, server.client().get("/guarded/x").status)
        assertEquals("ok", server.client().header("X-Tenant", "acme").get("/guarded/x").body)
        assertEquals(
            listOf("before /guarded/x", "after /guarded/x InvalidRequestException", "before /guarded/x", "after /guarded/x null"),
            events.toList(),
        )
        assertTrue(events.none { "zone" in it })
    }
}
