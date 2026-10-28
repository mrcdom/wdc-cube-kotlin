package br.com.wdc.framework.rest.error

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.exception.TransactionConflictException
import br.com.wdc.framework.domain.exception.TransactionLimitExceededException
import br.com.wdc.framework.rest.RequestContext
import br.com.wdc.framework.rest.security.AuthenticationException
import io.javalin.http.HttpResponseException

/** Resposta de erro: status, corpo (serializado como DTO; `null` = vazio) e cabeçalhos extras. */
data class ErrorResponse(val status: Int, val body: Any?, val headers: Map<String, String> = emptyMap())

/** Decide a resposta de uma exceção. `null` = "não é comigo": a busca segue para a superclasse. */
fun interface ExceptionHandler<in E : Throwable> {
    fun handle(e: E, request: RequestContext): ErrorResponse?
}

/**
 * Mapa de exceção para resposta, configurado pela aplicação.
 *
 * A busca vai da classe da exceção para as superclasses (e as interfaces de cada uma); vence o primeiro
 * handler que devolver resposta. Sem nenhum, [fallback] (500). Vêm registrados:
 *
 * | Exceção | Status |
 * |---|---|
 * | `InvalidRequestException` (inclui os parâmetros e corpos malformados) | 400 |
 * | `AuthenticationException` (sem credencial, ou credencial recusada) | `unauthenticatedStatus` (padrão 401) |
 * | `AccessDeniedException` | 403 |
 * | `TransactionConflictException` | 409 |
 * | `TransactionLimitExceededException` | 429 |
 * | `HttpResponseException` do Javalin | o status dela |
 *
 * Todos com o corpo de [errorBody]. Registrar a mesma classe de novo substitui o handler.
 */
class ExceptionMapping {

    private val handlers = LinkedHashMap<Class<*>, ExceptionHandler<Throwable>>()

    /** Status de "sem credencial"; a segurança o ajusta. */
    var unauthenticatedStatus: Int = 401

    /** O corpo dos erros padrão. Padrão: `{"error": "<mensagem>"}`. */
    var errorBody: (status: Int, message: String?, request: RequestContext) -> Any? = { _, message, _ -> mapOf("error" to message) }

    /** Exceção sem handler. Padrão: 500, com `errorBody(500, "Internal server error")`. */
    var fallback: (Throwable, RequestContext) -> ErrorResponse = { _, request ->
        ErrorResponse(500, errorBody(500, "Internal server error", request))
    }

    init {
        status(InvalidRequestException::class.java, 400)
        on(AuthenticationException::class.java) { e, r -> ErrorResponse(unauthenticatedStatus, errorBody(unauthenticatedStatus, e.message, r)) }
        status(AccessDeniedException::class.java, 403)
        status(TransactionConflictException::class.java, 409)
        status(TransactionLimitExceededException::class.java, 429)
        on(HttpResponseException::class.java) { e, r -> ErrorResponse(e.status, errorBody(e.status, e.message, r)) }
    }

    fun <E : Throwable> on(type: Class<E>, handler: ExceptionHandler<E>) {
        @Suppress("UNCHECKED_CAST")
        handlers[type] = handler as ExceptionHandler<Throwable>
    }

    inline fun <reified E : Throwable> on(handler: ExceptionHandler<E>) = on(E::class.java, handler)

    /** [type] responde [status], com a mensagem da exceção em [errorBody]. */
    fun status(type: Class<out Throwable>, status: Int) {
        on(type) { e, r -> ErrorResponse(status, errorBody(status, e.message, r)) }
    }

    /** Resposta de [e]. Nunca falha: erro dentro de um handler cai no [fallback] e é registrado. */
    fun resolve(e: Throwable, request: RequestContext): ErrorResponse {
        try {
            for (type in hierarchy(e.javaClass)) {
                handlers[type]?.handle(e, request)?.let { return it }
            }
        } catch (failure: Exception) {
            LOG.error("Falha no mapeamento da exceção ${e.javaClass.name}", failure)
        }
        return fallback(e, request)
    }

    private fun hierarchy(type: Class<*>): Sequence<Class<*>> = sequence {
        var current: Class<*>? = type
        val seen = HashSet<Class<*>>()
        while (current != null) {
            yield(current)
            for (i in current.interfaces) if (seen.add(i)) yield(i)
            current = current.superclass
        }
    }

    private companion object {
        val LOG = Log.getLogger("ExceptionMapping")
    }
}

/** Parâmetro de caminho, query ou cabeçalho ausente ou malformado. 400 pelo mapeamento padrão. */
open class RequestParameterException(val parameter: String, message: String) : InvalidRequestException(message)

/** Corpo da requisição ilegível (JSON malformado, tipo errado). 400 pelo mapeamento padrão. */
open class RequestBodyException(message: String, cause: Throwable? = null) : InvalidRequestException(message) {
    init {
        cause?.let { initCause(it) }
    }
}
