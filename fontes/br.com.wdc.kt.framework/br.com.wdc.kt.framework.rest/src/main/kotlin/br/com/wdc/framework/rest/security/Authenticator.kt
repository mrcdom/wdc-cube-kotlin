package br.com.wdc.framework.rest.security

import br.com.wdc.framework.rest.RequestContext

/**
 * Turns the request's credential into a [RestPrincipal]. The library doesn't know what a token is: the application
 * validates it (JWT, session, API key…) and returns the caller.
 *
 * - no credential → `null`;
 * - credential refused (expired, invalid signature…) → [AuthenticationException].
 *
 * Runs on every API route, public or not; on a public route a refused credential is ignored, unless
 * [SecurityConfig.rejectInvalidCredentialsOnPublicRoutes] says otherwise.
 */
fun interface Authenticator {
    suspend fun authenticate(request: RequestContext): RestPrincipal?
}

/** The credential came, but isn't valid. The response uses the status for a request without a valid credential. */
open class AuthenticationException(message: String) : RuntimeException(message)

/** No credential, on a route that requires one. */
open class AuthenticationRequiredException(message: String) : AuthenticationException(message)

/**
 * Reads `Authorization: Bearer <token>` and hands the token to [resolve]. `null` from [resolve] = token refused.
 *
 * @param scheme the scheme expected in the header, compared case-insensitively
 */
class BearerAuthenticator(
    private val scheme: String = "Bearer",
    private val header: String = "Authorization",
    private val resolve: suspend (token: String) -> RestPrincipal?,
) : Authenticator {

    override suspend fun authenticate(request: RequestContext): RestPrincipal? {
        val value = request.header(header)?.trim()
        if (value.isNullOrEmpty()) {
            return null
        }
        val prefix = "$scheme "
        if (!value.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)) {
            throw AuthenticationException("Missing or invalid $header header")
        }
        val token = value.substring(prefix.length).trim()
        if (token.isEmpty()) {
            throw AuthenticationException("Missing or invalid $header header")
        }
        return resolve(token) ?: throw AuthenticationException("Invalid or expired token")
    }
}
