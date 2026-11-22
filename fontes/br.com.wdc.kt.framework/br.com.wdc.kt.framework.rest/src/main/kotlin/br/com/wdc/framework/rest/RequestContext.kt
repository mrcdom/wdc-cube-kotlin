package br.com.wdc.framework.rest

import br.com.wdc.framework.rest.security.RestPrincipal
import io.javalin.http.Context
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.asContextElement

/**
 * Typed key of a request attribute. Compared by identity: create one instance per attribute, in a
 * `companion object` or an application `object`.
 */
class RequestKey<T : Any>(val name: String) {
    override fun toString(): String = "RequestKey($name)"
}

/**
 * What is known about the current request, reachable from any layer through [current] — even from inside
 * coroutines, on any thread: the route pipeline installs the context as a coroutine element.
 *
 * Born before the `before` filters and dies after the `after` ones. Headers are read from the request; the
 * attributes are free, for the application to keep what it extracts from it (client, time zone, app version…).
 */
class RequestContext internal constructor(
    /** The Javalin request. For code at the HTTP boundary; lower layers use the methods here. */
    val http: Context,
) {

    private val attributes = ConcurrentHashMap<RequestKey<*>, Any>()

    /** HTTP verb, uppercase. */
    val method: String get() = http.method().name()

    /** Requested path, without the query. */
    val path: String get() = http.path()

    /** The template of the route that served (`/api/user/{id}`), or the requested path if no route matched. */
    val matchedPath: String get() = runCatching { http.endpoints().lastHttpEndpoint()?.path }.getOrNull() ?: http.path()

    /** The caller, after authentication; `null` = anonymous, or security off. */
    var principal: RestPrincipal? = null
        internal set

    /** The principal as the application's type, or `null` if anonymous or of another type. */
    inline fun <reified P : RestPrincipal> principalAs(): P? = principal as? P

    fun header(name: String): String? = http.header(name)

    fun headers(name: String): List<String> = http.req().getHeaders(name)?.toList() ?: emptyList()

    fun queryParam(name: String): String? = http.queryParam(name)

    fun pathParam(name: String): String? = http.pathParamMap()[name]

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: RequestKey<T>): T? = attributes[key] as T?

    operator fun <T : Any> set(key: RequestKey<T>, value: T?) {
        if (value == null) attributes.remove(key) else attributes[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrPut(key: RequestKey<T>, compute: () -> T): T = attributes.getOrPut(key, compute) as T

    /** Coroutine element that keeps this context as the current one, on whatever thread it resumes. */
    fun asContextElement(): CoroutineContext.Element = CURRENT.asContextElement(this)

    companion object {
        private val CURRENT = ThreadLocal<RequestContext?>()

        /** The current request's context, or `null` outside a request. */
        fun currentOrNull(): RequestContext? = CURRENT.get()

        /** The current request's context. Outside a request, [IllegalStateException]. */
        fun current(): RequestContext = CURRENT.get() ?: throw IllegalStateException("No current request")

        /**
         * Runs [block] with [context] as the thread's current context, restoring the previous one at the end. It is
         * what the pipeline does around each route; it also serves whoever handles a request outside it.
         */
        fun <T> withCurrent(context: RequestContext?, block: () -> T): T {
            val previous = CURRENT.get()
            CURRENT.set(context)
            try {
                return block()
            } finally {
                if (previous == null) CURRENT.remove() else CURRENT.set(previous)
            }
        }

        internal fun of(ctx: Context): RequestContext = ctx.attributeOrCompute(ATTRIBUTE) { RequestContext(it) }!!

        internal const val ATTRIBUTE = "br.com.wdc.framework.rest.RequestContext"
    }
}
