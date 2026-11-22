package br.com.wdc.framework.rest.security

/**
 * The API's security. Without it ([br.com.wdc.framework.rest.RestApi.Builder.security] not called), every route
 * is open and the principal is always `null`.
 *
 * Order on each route: `before` filters → [authenticator] → per-minute limit ([rateLimit]) → the route's rule (or
 * [defaultRule]) → handler.
 */
class SecurityConfig {

    /** What turns the credential into a principal. Required. */
    var authenticator: Authenticator? = null

    /** The rule of routes that don't declare their own and aren't in [permitAll]. Default: requires authentication. */
    var defaultRule: AccessRule = AccessRules.authenticated

    /**
     * Status of the response to a caller without a valid credential on a route that requires one. Default 401;
     * Spring Security without an `AuthenticationEntryPoint` answers 403.
     */
    var unauthenticatedStatus: Int = 401

    /**
     * An anonymous request to a path no route serves (and outside [permitAll]) gets the "no credential" response
     * instead of 404/405 — as in Spring Security, where authorization comes before routing. Default: `false`
     * (Javalin's 404/405).
     */
    var protectUnmatched: Boolean = false

    /**
     * On a public route, a refused credential is ignored (the request proceeds anonymously). `true` refuses it with
     * the "no credential" response.
     */
    var rejectInvalidCredentialsOnPublicRoutes: Boolean = false

    internal var rateLimitOptions: RateLimitOptions? = null

    /** Turns on the per-minute request limit of each caller (see [RateLimitOptions]). */
    fun rateLimit(block: RateLimitOptions.() -> Unit) {
        rateLimitOptions = (rateLimitOptions ?: RateLimitOptions()).apply(block)
    }

    internal val publicMatchers = ArrayList<RequestMatcher>()

    /** Public routes, by path pattern with an optional verb: `"POST /auth/login"`, `"GET /catalog/featured"` (see [RequestMatcher]). */
    fun permitAll(vararg specs: String) {
        specs.mapTo(publicMatchers) { RequestMatcher.of(it) }
    }

    internal fun isPublic(method: String, path: String): Boolean = publicMatchers.any { it.matches(method, path) }
}
