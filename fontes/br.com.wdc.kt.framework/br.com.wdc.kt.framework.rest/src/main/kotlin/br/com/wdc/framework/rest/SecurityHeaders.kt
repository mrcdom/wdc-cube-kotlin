package br.com.wdc.framework.rest

import io.javalin.http.Context

/**
 * Cache and security headers on every API response ([RestApi.Builder.securityHeaders]): success, mapped error,
 * refusal by authentication or rule, 404/405 handled by the library and the CORS preflight.
 *
 * ```kotlin
 * securityHeaders {
 *     headers -= "X-XSS-Protection"                     // obsolete in current browsers
 *     headers["Strict-Transport-Security"] = "max-age=31536000"
 * }
 * ```
 */
class SecurityHeadersOptions {

    /**
     * Writes `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`, `Pragma: no-cache` and `Expires: 0` on
     * responses whose route didn't set its own `Cache-Control`. A route that wants caching writes its own
     * (`call.header(…)` or in the [RestResponse] headers), and the response goes out without `Pragma` or `Expires`.
     */
    var noCache: Boolean = true

    /**
     * Always written, replacing whatever the route set under the same name. Change a value, add or remove headers
     * freely. No `Strict-Transport-Security` by default: behind a proxy that terminates TLS, the request arrives
     * over HTTP.
     */
    val headers: MutableMap<String, String> = linkedMapOf(
        "X-Content-Type-Options" to "nosniff",
        "X-Frame-Options" to "DENY",
        "X-XSS-Protection" to "1; mode=block",
    )

    internal fun writeTo(ctx: Context) {
        if (noCache && ctx.res().getHeader(CACHE_CONTROL) == null) {
            ctx.header(CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate")
            ctx.header("Pragma", "no-cache")
            ctx.header("Expires", "0")
        }
        headers.forEach { (name, value) -> ctx.header(name, value) }
    }

    private companion object {
        const val CACHE_CONTROL = "Cache-Control"
    }
}
