package br.com.wdc.framework.rest.error

import br.com.wdc.framework.domain.exception.BusinessException
import br.com.wdc.framework.domain.exception.TransactionConflictException
import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.testing.RestTestServer
import io.javalin.http.NotFoundResponse
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ExceptionMappingTest {

    open class AppException(message: String) : RuntimeException(message)

    class UniqueException(val field: String, message: String) : AppException(message)

    class Teapot(message: String) : AppException(message)

    class Unexpected : RuntimeException("boom")

    private val api = RestApi {
        errors {
            errorBody = { status, message, _ -> mapOf("message" to message, "code" to status) }
            on<AppException> { e, _ -> ErrorResponse(400, mapOf("message" to e.message)) }
            on<UniqueException> { e, _ -> ErrorResponse(400, mapOf(e.field to e.message)) }
            // devolver null passa a vez para a superclasse
            on<Teapot> { e, _ -> if (e.message == "brew") ErrorResponse(418, null, mapOf("X-Pot" to "1")) else null }
            fallback = { _, _ -> ErrorResponse(500, mapOf("message" to "Não foi possível concluir a operação.")) }
        }
        get("/unique") { _ -> throw UniqueException("email", "já existe") }
        get("/app") { _ -> throw AppException("regra") }
        get("/teapot/{m}") { call -> throw Teapot(call.path("m")) }
        get("/business") { _ -> throw BusinessException("sem handler") }
        get("/conflict") { _ -> throw TransactionConflictException("tx") }
        get("/missing") { _ -> throw NotFoundResponse("não achei") }
        get("/boom") { _ -> throw Unexpected() }
        get("/param") { call -> call.pathLong("nope") }
    }

    private val server = RestTestServer(api)
    private val client = server.client()

    @AfterTest
    fun stop() = server.close()

    @Test
    fun mostSpecificHandlerWins() {
        assertEquals("""{"email":"já existe"}""", client.get("/unique").body)
        assertEquals("""{"message":"regra"}""", client.get("/app").body)
    }

    @Test
    fun nullDefersToTheSuperclass() {
        val brewed = client.get("/teapot/brew")
        assertEquals(418, brewed.status)
        assertEquals("", brewed.body)
        assertEquals("1", brewed.header("X-Pot"))
        assertEquals(400, client.get("/teapot/other").status)
    }

    @Test
    fun builtInMappings_useTheConfiguredErrorBody() {
        val conflict = client.get("/conflict")
        assertEquals(409, conflict.status)
        assertEquals("""{"message":"tx","code":409}""", conflict.body)
        assertEquals("""{"message":"não achei","code":404}""", client.get("/missing").body)
        assertEquals(400, client.get("/param").status)
    }

    @Test
    fun unmappedException_fallsBack() {
        val response = client.get("/boom")
        assertEquals(500, response.status)
        assertEquals("""{"message":"Não foi possível concluir a operação."}""", response.body)
        assertEquals(500, client.get("/business").status, "BusinessException não tem mapeamento padrão")
    }
}
