package br.com.wdc.framework.rest

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.rest.error.ErrorResponse
import br.com.wdc.framework.rest.error.ExceptionMapping
import br.com.wdc.framework.rest.json.GsonJsonMapper
import br.com.wdc.framework.rest.openapi.OpenApiConfig
import br.com.wdc.framework.rest.openapi.OpenApiGenerator
import br.com.wdc.framework.rest.openapi.OperationDoc
import br.com.wdc.framework.rest.security.AccessRule
import br.com.wdc.framework.rest.security.AccessRules
import br.com.wdc.framework.rest.security.AuthenticationException
import br.com.wdc.framework.rest.security.AuthenticationRequiredException
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

/** Filtro antes do handler. Pode lançar (a resposta é a da exceção) ou guardar atributos no contexto. */
typealias BeforeFilter = suspend (RequestContext) -> Unit

/** Filtro depois da resposta pronta: sempre roda, com a falha, se houve. Exceção aqui é registrada e ignorada. */
typealias AfterFilter = suspend (RequestContext, Throwable?) -> Unit

/**
 * Uma API REST: rotas, segurança, mapa de exceções, JSON, transação, CORS, compressão e OpenAPI — montada uma vez
 * e instalada num Javalin ([install]) ou servida sozinha ([createServer]).
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
 * **Pipeline de cada rota**, com o [RequestContext] corrente do começo ao fim — inclusive nas corrotinas:
 * filtros `before` → autenticação → regra de acesso → handler (na transação, se `transactional`) → resposta →
 * filtros `after`. A resposta, de sucesso ou de erro, é serializada **dentro** do contexto: um adaptador JSON
 * que dependa da requisição (fuso do cliente, por exemplo) a enxerga.
 */
class RestApi private constructor(builder: Builder) {

    /** O mapeador dos DTOs. */
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

    init {
        security?.let {
            requireNotNull(it.authenticator) { "security { authenticator = … } é obrigatório" }
            errors.unauthenticatedStatus = it.unauthenticatedStatus
        }
    }

    /** O documento OpenAPI das rotas registradas, ou `null` sem [Builder.openApi]. */
    val openApiDocument: Map<String, Any?>? by lazy { openApi?.let { OpenApiGenerator(this, it).document() } }

    /** O documento OpenAPI serializado, como é servido. */
    val openApiJson: String? by lazy { openApiDocument?.let { OpenApiGenerator.toJson(it) } }

    /** Registra rotas, filtros, CORS, compressão e limites no Javalin em configuração. */
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
            }
        }
        if (handleUnmatched || security?.protectUnmatched == true) {
            config.routes.exception(EndpointNotFound::class.java) { e, ctx -> unmatched(e, ctx) }
            config.routes.exception(MethodNotAllowedResponse::class.java) { e, ctx -> unmatched(e, ctx) }
        }
    }

    /** Um Javalin só com esta API, em threads virtuais. Para subir: `.start(porta)`. */
    fun createServer(configure: (JavalinConfig) -> Unit = {}): Javalin = Javalin.create { config ->
        config.concurrency.useVirtualThreads = true
        install(config)
        configure(config)
    }

    // :: Pipeline

    private fun handle(route: RestRoute, ctx: Context) {
        val request = RequestContext.of(ctx)
        RequestContext.withCurrent(request) {
            runBlocking(request.asContextElement()) {
                var failure: Throwable? = null
                try {
                    befores.forEach { (matcher, filter) -> if (matcher == null || matcher.matches(request.method, request.path)) filter(request) }
                    val urlPublic = route.public || security?.isPublic(request.method, request.path) == true
                    authenticate(request, urlPublic)
                    authorize(route, request, urlPublic)
                    val call = RestCall(request, this@RestApi)
                    val result = if (route.transactional) transactions.run(request) { route.handler(call) } else route.handler(call)
                    render(ctx, result)
                } catch (e: Throwable) {
                    failure = e
                    renderError(ctx, request, e)
                } finally {
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
        val request = RequestContext.of(ctx)
        RequestContext.withCurrent(request) {
            runBlocking(request.asContextElement()) {
                var failure: Throwable = e
                try {
                    befores.forEach { (matcher, filter) -> if (matcher == null || matcher.matches(request.method, request.path)) filter(request) }
                    val security = security
                    if (security?.protectUnmatched == true && !security.isPublic(request.method, request.path)) {
                        authenticate(request, urlPublic = false)
                        if (request.principal == null) throw AuthenticationRequiredException("Authentication required")
                    }
                    renderError(ctx, request, e)
                } catch (refusal: Throwable) {
                    failure = refusal
                    renderError(ctx, request, refusal)
                } finally {
                    runAfters(request, failure)
                }
            }
        }
    }

    private suspend fun runAfters(request: RequestContext, failure: Throwable?) {
        for ((matcher, filter) in afters) {
            if (matcher != null && !matcher.matches(request.method, request.path)) continue
            try {
                filter(request, failure)
            } catch (e: Exception) {
                LOG.error("Falha no filtro after de ${request.method} ${request.path}", e)
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
            LOG.error("Falha ao escrever a resposta de erro", failure)
            ctx.result("")
        }
    }

    // :: Montagem

    /** Montagem das rotas e da configuração. Um grupo ([group]) prefixa o caminho e soma regra e tags. */
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

        /** Rota com qualquer verbo. [path] no formato do Javalin: `{x}` um segmento, `<x>` vários. */
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
            require(root.routes.none { it.method == route.method && it.path == route.path }) { "Rota repetida: $route" }
            root.routes += route
            return route
        }

        /** Rotas sob [prefix], com a regra e as tags somadas às de cada uma. */
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

        /** A ponte de transação; as rotas de transação remota ([RemoteTransactionRoutes]) a recebem. */
        val transactions = RestTransactions()

        internal var corsOptions: CorsOptions? = null
        internal var compressionOptions: CompressionOptions? = null
        internal var openApiConfig: OpenApiConfig? = null

        /** O mapeador dos DTOs. Padrão: Gson sem nulos, datas ISO. */
        var json: GsonJsonMapper = GsonJsonMapper()

        /** Limite do corpo da requisição, em bytes; acima dele, 413. `null` = o do Javalin (1 MB). */
        var maxRequestSize: Long? = null

        /** Os 404/405 de caminho sem rota passam pelo mapa de exceções (o corpo de [ExceptionMapping.errorBody]). */
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

        fun openApi(block: OpenApiConfig.() -> Unit = {}) {
            openApiConfig = (openApiConfig ?: OpenApiConfig()).apply(block)
        }

        /** Filtro antes de cada rota (ou só das que casam [pattern], no formato de [RequestMatcher]). */
        fun before(pattern: String? = null, filter: BeforeFilter) {
            befores += pattern?.let { RequestMatcher.of(it) } to filter
        }

        fun after(pattern: String? = null, filter: AfterFilter) {
            afters += pattern?.let { RequestMatcher.of(it) } to filter
        }
    }

    companion object {
        private val LOG = Log.getLogger("RestApi")

        operator fun invoke(block: Builder.() -> Unit): RestApi = RestApi(Builder().apply(block))
    }
}
