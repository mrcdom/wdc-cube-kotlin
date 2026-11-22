package br.com.wdc.framework.rest

import br.com.wdc.framework.rest.openapi.OperationDoc
import br.com.wdc.framework.rest.security.AccessRule

/** A route's handler. The returned value is the response (see [RestCall]). */
typealias RestHandler = suspend (RestCall) -> Any?

/**
 * A registered route: verb, path template in Javalin's format (`/user/{id}`, `/files/<path>`) and what
 * surrounds the handler.
 */
class RestRoute internal constructor(
    val method: String,
    val path: String,
    /** The route's rule, combined with the path's (public or the default). `null` = only the path's. */
    val rule: AccessRule?,
    /** The route is public even outside the list of public routes. */
    val public: Boolean,
    /** The handler runs in the request transaction (see `RestTransactions`). */
    val transactional: Boolean,
    val doc: OperationDoc?,
    /** Left out of the OpenAPI description. */
    val hidden: Boolean,
    internal val handler: RestHandler,
) {
    override fun toString(): String = "$method $path"
}
