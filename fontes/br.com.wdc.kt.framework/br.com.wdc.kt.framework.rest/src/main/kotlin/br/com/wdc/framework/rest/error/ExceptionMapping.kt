package br.com.wdc.framework.rest.error

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.exception.NotFoundException
import br.com.wdc.framework.domain.exception.TransactionConflictException
import br.com.wdc.framework.domain.exception.TransactionLimitExceededException
import br.com.wdc.framework.domain.transaction.RemoteTransactionNotFoundException
import br.com.wdc.framework.rest.RequestContext
import br.com.wdc.framework.rest.security.AuthenticationException
import br.com.wdc.framework.rest.security.RateLimitedException
import io.javalin.http.HttpResponseException

/** Error response: status, body (serialized as a DTO; `null` = empty) and extra headers. */
data class ErrorResponse(val status: Int, val body: Any?, val headers: Map<String, String> = emptyMap())

/** Decides the response for an exception. `null` = "not mine": the lookup moves on to the superclass. */
fun interface ExceptionHandler<in E : Throwable> {
    fun handle(e: E, request: RequestContext): ErrorResponse?
}

/**
 * Exception-to-response map, configured by the application.
 *
 * The lookup goes from the exception's class to its superclasses (and each one's interfaces); the first handler
 * that returns a response wins. With none, [fallback] (500). Registered by default:
 *
 * | Exception | Status |
 * |---|---|
 * | `InvalidRequestException` (includes malformed parameters and bodies) | 400 |
 * | `AuthenticationException` (no credential, or credential refused) | `unauthenticatedStatus` (default 401) |
 * | `AccessDeniedException` | 403 |
 * | `NotFoundException` | 404 |
 * | `TransactionConflictException` (including a remote transaction in use by another request) | 409 |
 * | `RemoteTransactionNotFoundException` (unknown or expired remote transaction) | 410 |
 * | `TransactionLimitExceededException` | 429 |
 * | `RateLimitedException` (per-minute limit) | 429, with `Retry-After` |
 * | Javalin's `HttpResponseException` | its status |
 *
 * All with the body from [errorBody]. Registering the same class again replaces the handler.
 */
class ExceptionMapping {

    private val handlers = LinkedHashMap<Class<*>, ExceptionHandler<Throwable>>()

    /** Status for "no credential"; security adjusts it. */
    var unauthenticatedStatus: Int = 401

    /** The body of the default errors. Default: `{"error": "<message>"}`. */
    var errorBody: (status: Int, message: String?, request: RequestContext) -> Any? = { _, message, _ -> mapOf("error" to message) }

    /** Exception without a handler. Default: 500, with `errorBody(500, "Internal server error")`. */
    var fallback: (Throwable, RequestContext) -> ErrorResponse = { _, request ->
        ErrorResponse(500, errorBody(500, "Internal server error", request))
    }

    init {
        status(InvalidRequestException::class.java, 400)
        on(AuthenticationException::class.java) { e, r -> ErrorResponse(unauthenticatedStatus, errorBody(unauthenticatedStatus, e.message, r)) }
        status(AccessDeniedException::class.java, 403)
        status(NotFoundException::class.java, 404)
        status(TransactionConflictException::class.java, 409)
        status(RemoteTransactionNotFoundException::class.java, 410)
        status(TransactionLimitExceededException::class.java, 429)
        on(RateLimitedException::class.java) { e, r ->
            ErrorResponse(429, errorBody(429, e.message, r), mapOf("Retry-After" to e.retryAfterSeconds.toString()))
        }
        on(HttpResponseException::class.java) { e, r -> ErrorResponse(e.status, errorBody(e.status, e.message, r)) }
    }

    fun <E : Throwable> on(type: Class<E>, handler: ExceptionHandler<E>) {
        @Suppress("UNCHECKED_CAST")
        handlers[type] = handler as ExceptionHandler<Throwable>
    }

    inline fun <reified E : Throwable> on(handler: ExceptionHandler<E>) = on(E::class.java, handler)

    /** [type] answers [status], with the exception's message in [errorBody]. */
    fun status(type: Class<out Throwable>, status: Int) {
        on(type) { e, r -> ErrorResponse(status, errorBody(status, e.message, r)) }
    }

    /** Response for [e]. Never fails: an error inside a handler falls into [fallback] and is logged. */
    fun resolve(e: Throwable, request: RequestContext): ErrorResponse {
        try {
            for (type in hierarchy(e.javaClass)) {
                handlers[type]?.handle(e, request)?.let { return it }
            }
        } catch (failure: Exception) {
            LOG.error("Failed to map exception ${e.javaClass.name}", failure)
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

/** Missing or malformed path, query or header parameter. 400 by the default mapping. */
open class RequestParameterException(val parameter: String, message: String) : InvalidRequestException(message)

/** Unreadable request body (malformed JSON, wrong type). 400 by the default mapping. */
open class RequestBodyException(message: String, cause: Throwable? = null) : InvalidRequestException(message) {
    init {
        cause?.let { initCause(it) }
    }
}
