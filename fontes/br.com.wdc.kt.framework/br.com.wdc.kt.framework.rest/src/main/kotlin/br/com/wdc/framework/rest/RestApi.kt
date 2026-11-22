package br.com.wdc.framework.rest

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.rest.error.ErrorResponse
import br.com.wdc.framework.rest.error.ExceptionMapping
import br.com.wdc.framework.rest.json.GsonJsonMapper
import br.com.wdc.framework.rest.openapi.OpenApiConfig
import br.com.wdc.framework.rest.openapi.OpenApiGenerator
import br.com.wdc.framework.rest.openapi.OperationDoc
import br.com.wdc.framework.rest.openapi.SwaggerUiRoutes
import br.com.wdc.framework.rest.security.AccessRule
import br.com.wdc.framework.rest.security.AccessRules
import br.com.wdc.framework.rest.security.AuthenticationException
import br.com.wdc.framework.rest.security.AuthenticationRequiredException
import br.com.wdc.framework.rest.security.RateLimiter
import br.com.wdc.framework.rest.security.RequestMatcher
import br.com.wdc.framework.rest.security.SecurityConfig
import br.com.wdc.framework.rest.transaction.RemoteTransactionRoutes
import br.com.wdc.framework.rest.transaction.RestTransactions
import io.javalin.Javalin
import io.javalin.compression.CompressionStrategy
import io.javalin.compression.Gzip
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import io.javalin.http.HandlerType
import io.javalin.http.MethodNotAllowedResponse
import io.javalin.router.EndpointNotFound
import java.io.InputStream
import kotlinx.coroutines.runBlocking

/** Filter before the handler. It may throw (the response is the exception's) or store attributes in the context. */
typealias BeforeFilter = suspend (RequestContext) -> Unit

/** Filter after the response is ready: always runs, with the failure, if any. An exception here is logged and ignored. */
typealias AfterFilter = suspend (RequestContext, Throwable?) -> Unit

/**
 * A REST API: routes, security, exception map, JSON, transaction, CORS, compression and OpenAPI — built once
 * and installed into a Javalin ([install]) or served on its own ([createServer]).
 *
 * ```kotlin
 * val api = RestApi {
 *     json = GsonJsonMapper(JsonOptions.springDefaults())
 *     security {
 *         authenticator = BearerAuthenticator { token -> jwt.resolve(token) }
 *         unauthenticatedStatus = 403
 *         permitAll("POST /auth/login", "GET /catalog/featured")
 *     }
 *     errors { on<UniqueException> { e, _ -> ErrorResponse(400, mapOf(e.field to e.message)) } }
 *     before { r -> r[APP_VERSION] = r.header("X-App-Version") }
 *     group("/order", tags = listOf("Order")) {
 *         get("/{id}", rule = roles.hasRole("ADMIN")) { call -> orders.find(call.pathLong("id")) }
 *     }
 * }
 * ```
 *
 * **Pipeline of each route**, with the [RequestContext] current from start to end — coroutines included:
 * `before` filters → authentication → per-minute limit → access rule → handler (in the transaction, if `transactional`) → response →
 * `after` filters. The response, success or error, is serialized **inside** the context: a JSON adapter
 * that depends on the request (the client's time zone, for example) sees it. With [Builder.securityHeaders] and
 * [Builder.serverTiming], their headers are written after the response is serialized, before the `after` filters.
 */
class RestApi private constructor(builder: Builder) {

    /** The DTO mapper. */
    val json: GsonJsonMapper = builder.json

    val security: SecurityConfig? = builder.securityConfig

    val errors: ExceptionMapping = builder.errors

    val transactions: RestTransactions = builder.transactions

    val routes: List<RestRoute> = builder.routes.toList()

    private val befores = builder.befores.toList()
    private val afters = builder.afters.toList()
    private val cors = builder.corsOptions
    private val compression = builder.compressionOptions
    private val maxRequestSize = builder.maxRequestSize
    private val handleUnmatched = builder.handleUnmatched
    private val openApi = builder.openApiConfig
    private val serverTiming = builder.serverTimingOptions
    private val securityHeaders = builder.securityHeadersOptions
    private val rateLimiter = builder.securityConfig?.rateLimitOptions?.let(::RateLimiter)

    init {
        security?.let {
            requireNotNull(it.authenticator) { "security { authenticator = … } is required" }
            errors.unauthenticatedStatus = it.unauthenticatedStatus
        }
    }

    /** The OpenAPI document of the registered routes, or `null` without [Builder.openApi]. */
    val openApiDocument: Map<String, Any?>? by lazy { openApi?.let { OpenApiGenerator(this, it).document() } }

    /** The serialized OpenAPI document, as served. */
    val openApiJson: String? by lazy { openApiDocument?.let { OpenApiGenerator.toJson(it) } }

    /** Registers routes, filters, CORS, compression and limits on the Javalin being configured. */
    fun install(config: JavalinConfig) {
        cors?.let { options ->
            config.bundledPlugins.enableCors { plugin ->
                plugin.addRule { rule ->
                    if (options.anyOrigin) rule.anyHost()
                    if (options.origins.isNotEmpty()) rule.allowHost(options.origins.first(), *options.origins.drop(1).toTypedArray())
                    rule.allowCredentials = options.allowCredentials
                    rule.reflectClientOrigin = options.reflectClientOrigin
                    rule.maxAge = options.maxAge
                    rule.path = options.path
                    options.exposedHeaders.forEach(rule::exposeHeader)
                    // unless exposed, the browser hides the header from callers on another origin
                    if (serverTiming != null && options.exposedHeaders.none { it.equals(ServerTiming.HEADER, ignoreCase = true) }) {
                        rule.exposeHeader(ServerTiming.HEADER)
                    }
                }
            }
        }
        compression?.let { options ->
            val strategy = CompressionStrategy(null, Gzip(options.gzipLevel))
            strategy.defaultMinSizeForCompression = options.minSize
            if (options.mimeTypes.isNotEmpty()) strategy.allowedMimeTypes = options.mimeTypes.toList()
            config.http.compressionStrategy = strategy
        }
        maxRequestSize?.let { config.http.maxRequestSize = it }

        for (route in routes) {
            config.routes.addHttpHandler(HandlerType.findOrCreate(route.method), route.path) { ctx -> handle(route, ctx) }
        }
        openApi?.let { cfg ->
            config.routes.get(cfg.path) { ctx ->
                ctx.contentType("application/json")
                ctx.result(openApiJson!!)
                securityHeaders?.writeTo(ctx)
            }
        }
        // the CORS preflight is answered by the plugin, without going through a route or unmatched
        val corsPath = cors?.path
        if (securityHeaders != null && corsPath != null) {
            config.routes.after(corsPath) { ctx -> if (isPreflight(ctx)) securityHeaders.writeTo(ctx) }
        }
        if (handleUnmatched || security?.protectUnmatched == true) {
            config.routes.exception(EndpointNotFound::class.java) { e, ctx -> unmatched(e, ctx) }
            config.routes.exception(MethodNotAllowedResponse::class.java) { e, ctx -> unmatched(e, ctx) }
        }
    }

    /** A Javalin with only this API, on virtual threads. To start it: `.start(port)`. */
    fun createServer(configure: (JavalinConfig) -> Unit = {}): Javalin = Javalin.create { config ->
        config.concurrency.useVirtualThreads = true
        install(config)
        configure(config)
    }

    // :: Pipeline

    private fun handle(route: RestRoute, ctx: Context) {
        val request = RequestContext.of(ctx)
        startTiming(request)
        RequestContext.withCurrent(request) {
            runBlocking(request.asContextElement()) {
                var failure: Throwable? = null
                try {
                    befores.forEach { (matcher, filter) -> if (matcher == null || matcher.matches(request.method, request.path)) filter(request) }
                    val urlPublic = route.public || security?.isPublic(request.method, request.path) == true
                    authenticate(request, urlPublic)
                    request.principal?.let { rateLimiter?.check(it) }
                    authorize(route, request, urlPublic)
                    val call = RestCall(request, this@RestApi)
                    val result = if (route.transactional) transactions.run(request) { route.handler(call) } else route.handler(call)
                    render(ctx, result)
                } catch (e: Throwable) {
                    failure = e
                    renderError(ctx, request, e)
                } finally {
                    writeSecurityHeaders(ctx, request)
                    writeTiming(ctx, request)
                    runAfters(request, failure)
                }
            }
        }
    }

    private suspend fun authenticate(request: RequestContext, urlPublic: Boolean) {
        val security = security ?: return
        request.principal = try {
            security.authenticator!!.authenticate(request)
        } catch (e: AuthenticationException) {
            if (urlPublic && !security.rejectInvalidCredentialsOnPublicRoutes) null else throw e
        }
    }

    private fun authorize(route: RestRoute, request: RequestContext, urlPublic: Boolean) {
        val security = security ?: return
        val urlRule = if (urlPublic) AccessRules.permitAll else security.defaultRule
        val rule = route.rule?.let { urlRule and it } ?: urlRule
        if (rule.allows(request)) return
        if (request.principal == null && rule.requiresAuthentication) {
            throw AuthenticationRequiredException("Authentication required")
        }
        throw AccessDeniedException("Access denied")
    }

    private fun unmatched(e: Exception, ctx: Context) {
        // The CORS preflight has no route (the plugin only sets the headers) and comes without a credential: treated as
        // a nonexistent route, protectUnmatched would refuse it and the browser would block every cross-origin request.
        if (isPreflight(ctx)) {
            ctx.status(200)
            return
        }
        val request = RequestContext.of(ctx)
        startTiming(request)
        RequestContext.withCurrent(request) {
            runBlocking(request.asContextElement()) {
                var failure: Throwable = e
                try {
                    befores.forEach { (matcher, filter) -> if (matcher == null || matcher.matches(request.method, request.path)) filter(request) }
                    val security = security
                    if (security?.protectUnmatched == true && !security.isPublic(request.method, request.path)) {
                        authenticate(request, urlPublic = false)
                        if (request.principal == null) throw AuthenticationRequiredException("Authentication required")
                        rateLimiter?.check(request.principal!!)
                    }
                    renderError(ctx, request, e)
                } catch (refusal: Throwable) {
                    failure = refusal
                    renderError(ctx, request, refusal)
                } finally {
                    writeSecurityHeaders(ctx, request)
                    writeTiming(ctx, request)
                    runAfters(request, failure)
                }
            }
        }
    }

    private fun isPreflight(ctx: Context): Boolean =
        cors != null && ctx.method() == HandlerType.OPTIONS && ctx.header("Access-Control-Request-Method") != null

    private fun writeSecurityHeaders(ctx: Context, request: RequestContext) {
        val options = securityHeaders ?: return
        try {
            options.writeTo(ctx)
        } catch (e: Exception) {
            LOG.error("Failed to write the security headers of ${request.method} ${request.path}", e)
        }
    }

    private fun startTiming(request: RequestContext) {
        serverTiming?.let { request[ServerTiming.KEY] = ServerTiming(System.nanoTime(), it.names) }
    }

    /** After the response is serialized (which counts in `total`) and before it goes out. */
    private fun writeTiming(ctx: Context, request: RequestContext) {
        val timing = request[ServerTiming.KEY] ?: return
        try {
            ctx.header(ServerTiming.HEADER, timing.header(System.nanoTime()))
        } catch (e: Exception) {
            LOG.error("Failed to write the Server-Timing of ${request.method} ${request.path}", e)
        }
    }

    private suspend fun runAfters(request: RequestContext, failure: Throwable?) {
        for ((matcher, filter) in afters) {
            if (matcher != null && !matcher.matches(request.method, request.path)) continue
            try {
                filter(request, failure)
            } catch (e: Exception) {
                LOG.error("After filter failed on ${request.method} ${request.path}", e)
            }
        }
    }

    private fun render(ctx: Context, result: Any?) {
        when (result) {
            null, Unit -> Unit
            is RestResponse -> {
                ctx.status(result.status)
                result.headers.forEach { (name, value) -> ctx.header(name, value) }
                render(ctx, result.body)
                result.contentType?.let { ctx.contentType(it) }
            }
            is RawJson -> {
                ctx.contentType("application/json")
                ctx.result(result.json)
            }
            is String -> {
                ctx.contentType("text/plain; charset=utf-8")
                ctx.result(result)
            }
            is ByteArray -> {
                ctx.contentType("application/octet-stream")
                ctx.result(result)
            }
            is InputStream -> {
                ctx.contentType("application/octet-stream")
                ctx.result(result)
            }
            else -> {
                val text = json.toJsonString(result, result.javaClass)
                ctx.contentType("application/json")
                ctx.result(text)
            }
        }
    }

    private fun renderError(ctx: Context, request: RequestContext, e: Throwable) {
        val response: ErrorResponse = errors.resolve(e, request)
        if (response.status >= 500) {
            LOG.error("${request.method} ${request.path} → ${response.status}", e)
        } else {
            LOG.debug("{} {} → {}: {}", request.method, request.path, response.status, e.message)
        }
        ctx.status(response.status)
        response.headers.forEach { (name, value) -> ctx.header(name, value) }
        try {
            if (response.body == null) ctx.result("") else render(ctx, response.body)
        } catch (failure: Exception) {
            LOG.error("Failed to write the error response", failure)
            ctx.result("")
        }
    }

    // :: Assembly

    /** Assembly of the routes and configuration. A group ([group]) prefixes the path and adds rule and tags. */
    open class Routes internal constructor(
        private val rootRef: Builder?,
        private val prefix: String,
        private val groupRule: AccessRule?,
        private val groupTags: List<String>,
    ) {
        internal val root: Builder get() = rootRef ?: this as Builder

        fun get(path: String, rule: AccessRule? = null, public: Boolean = false, transactional: Boolean = false, hidden: Boolean = false, doc: (OperationDoc.() -> Unit)? = null, handler: RestHandler) =
            route("GET", path, rule, public, transactional, hidden, doc, handler)

        fun post(path: String, rule: AccessRule? = null, public: Boolean = false, transactional: Boolean = false, hidden: Boolean = false, doc: (OperationDoc.() -> Unit)? = null, handler: RestHandler) =
            route("POST", path, rule, public, transactional, hidden, doc, handler)

        fun put(path: String, rule: AccessRule? = null, public: Boolean = false, transactional: Boolean = false, hidden: Boolean = false, doc: (OperationDoc.() -> Unit)? = null, handler: RestHandler) =
            route("PUT", path, rule, public, transactional, hidden, doc, handler)

        fun patch(path: String, rule: AccessRule? = null, public: Boolean = false, transactional: Boolean = false, hidden: Boolean = false, doc: (OperationDoc.() -> Unit)? = null, handler: RestHandler) =
            route("PATCH", path, rule, public, transactional, hidden, doc, handler)

        fun delete(path: String, rule: AccessRule? = null, public: Boolean = false, transactional: Boolean = false, hidden: Boolean = false, doc: (OperationDoc.() -> Unit)? = null, handler: RestHandler) =
            route("DELETE", path, rule, public, transactional, hidden, doc, handler)

        /** Route with any verb. [path] in Javalin's format: `{x}` one segment, `<x>` several. */
        fun route(
            method: String,
            path: String,
            rule: AccessRule? = null,
            public: Boolean = false,
            transactional: Boolean = false,
            hidden: Boolean = false,
            doc: (OperationDoc.() -> Unit)? = null,
            handler: RestHandler,
        ): RestRoute {
            val fullPath = join(prefix, path)
            val effectiveRule = when {
                groupRule == null -> rule
                rule == null -> groupRule
                else -> groupRule and rule
            }
            val operation = if (doc != null || groupTags.isNotEmpty()) OperationDoc().also { op ->
                op.tags += groupTags
                doc?.invoke(op)
            } else null
            val route = RestRoute(method.uppercase(), fullPath, effectiveRule, public, transactional, operation, hidden, handler)
            require(root.routes.none { it.method == route.method && it.path == route.path }) { "Duplicate route: $route" }
            root.routes += route
            return route
        }

        /** Routes under [prefix], with the rule and tags added to each one's. */
        fun group(prefix: String, rule: AccessRule? = null, tags: List<String> = emptyList(), block: Routes.() -> Unit) {
            val combined = when {
                groupRule == null -> rule
                rule == null -> groupRule
                else -> groupRule and rule
            }
            Routes(root, join(this.prefix, prefix), combined, groupTags + tags).block()
        }

        private fun join(a: String, b: String): String {
            if (a.isEmpty()) return b
            if (b.isEmpty() || b == "/") return a
            return a.trimEnd('/') + "/" + b.trimStart('/')
        }
    }

    class Builder internal constructor() : Routes(null, "", null, emptyList()) {

        internal val routes = ArrayList<RestRoute>()
        internal val befores = ArrayList<Pair<RequestMatcher?, BeforeFilter>>()
        internal val afters = ArrayList<Pair<RequestMatcher?, AfterFilter>>()
        internal var securityConfig: SecurityConfig? = null
        internal val errors = ExceptionMapping()

        /** The transaction bridge; the remote transaction routes ([RemoteTransactionRoutes]) receive it. */
        val transactions = RestTransactions()

        internal var corsOptions: CorsOptions? = null
        internal var compressionOptions: CompressionOptions? = null
        internal var openApiConfig: OpenApiConfig? = null
        internal var serverTimingOptions: ServerTimingOptions? = null
        internal var securityHeadersOptions: SecurityHeadersOptions? = null

        /** The DTO mapper. Default: Gson without nulls, ISO dates. */
        var json: GsonJsonMapper = GsonJsonMapper()

        /** Request body limit, in bytes; above it, 413. `null` = Javalin's (1 MB). */
        var maxRequestSize: Long? = null

        /** The 404/405 of paths without a route go through the exception map (the body of [ExceptionMapping.errorBody]). */
        var handleUnmatched: Boolean = false

        fun security(block: SecurityConfig.() -> Unit) {
            securityConfig = (securityConfig ?: SecurityConfig()).apply(block)
        }

        fun errors(block: ExceptionMapping.() -> Unit) {
            errors.block()
        }

        fun transactions(block: RestTransactions.() -> Unit) {
            transactions.block()
        }

        fun cors(block: CorsOptions.() -> Unit) {
            corsOptions = (corsOptions ?: CorsOptions()).apply(block)
        }

        fun compression(block: CompressionOptions.() -> Unit = {}) {
            compressionOptions = (compressionOptions ?: CompressionOptions()).apply(block)
        }

        /** Turns on the `Server-Timing` header (see [ServerTimingOptions]); off if not called. */
        fun serverTiming(block: ServerTimingOptions.() -> Unit = {}) {
            serverTimingOptions = (serverTimingOptions ?: ServerTimingOptions()).apply(block)
        }

        /** Turns on the cache and security headers on every response (see [SecurityHeadersOptions]); off if not called. */
        fun securityHeaders(block: SecurityHeadersOptions.() -> Unit = {}) {
            securityHeadersOptions = (securityHeadersOptions ?: SecurityHeadersOptions()).apply(block)
        }

        fun openApi(block: OpenApiConfig.() -> Unit = {}) {
            openApiConfig = (openApiConfig ?: OpenApiConfig()).apply(block)
        }

        /** The Swagger page routes, public and left out of the document; mounted after all the others. */
        internal fun addSwaggerUi() {
            val config = openApiConfig ?: return
            val options = config.uiOptions ?: return
            val ui = SwaggerUiRoutes(options, config)
            get(ui.root + ".html", public = true, hidden = true, handler = ui.redirect)
            get(ui.root + "/{file}", public = true, hidden = true, handler = ui.file)
        }

        /** Filter before each route (or only those matching [pattern], in [RequestMatcher]'s format). */
        fun before(pattern: String? = null, filter: BeforeFilter) {
            befores += pattern?.let { RequestMatcher.of(it) } to filter
        }

        fun after(pattern: String? = null, filter: AfterFilter) {
            afters += pattern?.let { RequestMatcher.of(it) } to filter
        }
    }

    companion object {
        private val LOG = Log.getLogger("RestApi")

        operator fun invoke(block: Builder.() -> Unit): RestApi = RestApi(Builder().apply(block).apply { addSwaggerUi() })
    }
}
