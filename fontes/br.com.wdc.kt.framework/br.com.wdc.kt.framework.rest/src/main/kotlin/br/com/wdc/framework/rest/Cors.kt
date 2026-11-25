package br.com.wdc.framework.rest

import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import io.javalin.http.HandlerType
import io.javalin.http.HttpStatus
import java.net.URI

/**
 * CORS on a Javalin server, as [CorsOptions] describes it. Javalin's bundled plugin does the same, with two
 * differences that matter:
 *
 * - **A request from the server's own origin is not a CORS request.** Browsers send `Origin` on same-origin requests
 *   too (module scripts, `fetch` with a body): those pass untouched, whatever the allowed origins are. The plugin
 *   refused them with 400 unless the server's own origin was listed, so a server on another host or port broke its
 *   own pages. The server's origin is the request's `Host`.
 * - **A request from an origin not allowed is answered without the CORS headers**, as the CORS specification has it:
 *   the browser hides the answer from the page. The plugin answered 400, which hid the request from the server's
 *   logic too, and from same-origin callers that happened to send `Origin`.
 *
 * The preflight (`OPTIONS`) is answered before the routes and security: the methods and headers the browser asks for
 * are accepted, and an `OPTIONS` without a route answers 200.
 *
 * ```kotlin
 * Javalin.create { config ->
 *     Cors.install(config) { allowOrigins("https://app.example"); allowCredentials = true }
 * }
 * ```
 * A [RestApi] with `cors { }` installs it on its own.
 */
class Cors private constructor(private val options: CorsOptions, private val exposedHeaders: List<String>) {

    companion object {
        /** Installs CORS on [config], configured by [block]. */
        fun install(config: JavalinConfig, block: CorsOptions.() -> Unit) = install(config, CorsOptions().apply(block))

        /** Installs CORS on [config] as [options] says, exposing also [extraExposedHeaders]. */
        fun install(config: JavalinConfig, options: CorsOptions, extraExposedHeaders: List<String> = emptyList()) {
            Cors(options, (options.exposedHeaders + extraExposedHeaders).distinct()).register(config)
        }

        private val DEFAULT_PORTS = mapOf("http" to 80, "https" to 443)
    }

    /** An origin as its parts; [host] may start with `*.` (any subdomain) in an allowed origin. */
    private data class Origin(val scheme: String, val host: String, val port: Int) {
        fun matches(other: Origin): Boolean {
            if (scheme != other.scheme || port != other.port) return false
            if (!host.startsWith("*.")) return host == other.host
            return other.host.endsWith(host.substring(1))
        }
    }

    private val allowed: List<Origin> = options.origins.map { entry ->
        val withScheme = if ("://" in entry) entry else "https://$entry"
        parse(withScheme) ?: throw IllegalArgumentException("Not a valid origin: '$entry'")
    }

    init {
        require(options.anyOrigin || allowed.isNotEmpty() || options.reflectClientOrigin) {
            "CORS needs allowed origins, anyOrigin() or reflectClientOrigin"
        }
        require(!(options.anyOrigin && options.allowCredentials)) {
            "anyOrigin() does not combine with allowCredentials (browsers refuse it): use reflectClientOrigin"
        }
    }

    private fun register(config: JavalinConfig) {
        config.routes.before(options.path) { ctx -> handle(ctx) }
        config.routes.after(options.path) { ctx ->
            // CORS is on: an OPTIONS with no route of its own is a preflight, and it is answered
            if (ctx.method() == HandlerType.OPTIONS &&
                (ctx.status() == HttpStatus.NOT_FOUND || ctx.status() == HttpStatus.METHOD_NOT_ALLOWED)
            ) {
                ctx.result("").status(HttpStatus.OK)
            }
        }
    }

    private fun handle(ctx: Context) {
        val originHeader = ctx.header("Origin") ?: return
        val origin = parse(originHeader) ?: return
        if (isSameOrigin(ctx, origin)) return

        val allowOrigin = when {
            options.anyOrigin -> "*"
            options.reflectClientOrigin -> originHeader
            allowed.any { it.matches(origin) } -> originHeader
            // not allowed: no CORS headers, and the browser hides the answer from the page
            else -> return
        }

        if (ctx.method() == HandlerType.OPTIONS) {
            var requested = false
            ctx.header("Access-Control-Request-Headers")?.let { ctx.header("Access-Control-Allow-Headers", it); requested = true }
            ctx.header("Access-Control-Request-Method")?.let { ctx.header("Access-Control-Allow-Methods", it); requested = true }
            if (requested && options.maxAge >= 0) ctx.header("Access-Control-Max-Age", options.maxAge.toString())
        }
        ctx.header("Access-Control-Allow-Origin", allowOrigin)
        ctx.header("Vary", "Origin")
        if (options.allowCredentials) ctx.header("Access-Control-Allow-Credentials", "true")
        if (exposedHeaders.isNotEmpty()) ctx.header("Access-Control-Expose-Headers", exposedHeaders.joinToString(","))
    }

    /** Whether [origin] is the server's own: its host and port are the ones the request was sent to. */
    private fun isSameOrigin(ctx: Context, origin: Origin): Boolean {
        val (host, port) = splitAuthority(ctx.header("Host") ?: return false) ?: return false
        return host.equals(origin.host, ignoreCase = true) && (port ?: DEFAULT_PORTS[origin.scheme]) == origin.port
    }

    private fun splitAuthority(authority: String): Pair<String, Int?>? {
        val bracket = authority.lastIndexOf(']')
        val colon = authority.lastIndexOf(':')
        return if (colon > bracket) {
            authority.substring(0, colon) to (authority.substring(colon + 1).toIntOrNull() ?: return null)
        } else {
            authority to null
        }
    }

    private fun parse(text: String): Origin? {
        if (text == "null") return null
        // "*." is not a valid host for URI: parse a stand-in and put the wildcard back
        val wildcard = "://*." in text
        val uri = runCatching { URI(if (wildcard) text.replace("://*.", "://wildcard.") else text) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        // a scheme with no default port (an app's own, like tauri://) and no port written: -1, compared as is
        val port = if (uri.port >= 0) uri.port else DEFAULT_PORTS[scheme] ?: -1
        return Origin(scheme, if (wildcard) "*." + host.removePrefix("wildcard.") else host, port)
    }
}
