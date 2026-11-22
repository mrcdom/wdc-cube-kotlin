package br.com.wdc.framework.rest.security

import br.com.wdc.framework.rest.RequestContext

/**
 * Access rule of a route — the equivalent of `@PreAuthorize`. It decides with what is already known about the
 * request: the principal, the path and query parameters, the headers. A rule that depends on the body or on database
 * data stays in the handler, which throws `AccessDeniedException`.
 *
 * [requiresAuthentication] says whether an anonymous caller is refused as "no credential" before the rule is
 * consulted — the two cases may have different status and body ([SecurityConfig]).
 */
interface AccessRule {

    /** `true` if the route requires a principal. An anonymous caller gets the "no credential" response, not "forbidden". */
    val requiresAuthentication: Boolean

    fun allows(request: RequestContext): Boolean

    infix fun and(other: AccessRule): AccessRule = Composite(this, other, all = true)

    infix fun or(other: AccessRule): AccessRule = Composite(this, other, all = false)

    private class Composite(val a: AccessRule, val b: AccessRule, val all: Boolean) : AccessRule {
        override val requiresAuthentication: Boolean =
            if (all) a.requiresAuthentication || b.requiresAuthentication else a.requiresAuthentication && b.requiresAuthentication

        override fun allows(request: RequestContext): Boolean =
            if (all) a.allows(request) && b.allows(request) else a.allows(request) || b.allows(request)
    }
}

/**
 * Role rules with a prefix, like Spring Security: `hasRole("ADMIN")` and `hasRole("ROLE_ADMIN")` accept
 * the role `ROLE_ADMIN` when the prefix is `ROLE_`. With an empty prefix, the comparison is exact.
 */
class RoleRules(private val prefix: String) {

    private fun matches(principal: RestPrincipal, role: String): Boolean =
        principal.hasRole(role) || (prefix.isNotEmpty() && !role.startsWith(prefix) && principal.hasRole(prefix + role))

    /** `hasRole('X')`. */
    fun hasRole(role: String): AccessRule = AccessRules.rule { r -> r.principal?.let { matches(it, role) } == true }

    /** `hasAnyRole('X', 'Y')`. */
    fun hasAnyRole(vararg roles: String): AccessRule =
        AccessRules.rule { r -> r.principal?.let { p -> roles.any { matches(p, it) } } == true }
}

/** The commonly used rules. */
object AccessRules {

    private val exactRoles = RoleRules("")

    /** Anyone, authenticated or not (`permitAll`). */
    val permitAll: AccessRule = rule(requiresAuthentication = false) { true }

    /** Anyone authenticated (`isAuthenticated()`). */
    val authenticated: AccessRule = rule { it.principal != null }

    /** No one (`denyAll`). */
    val denyAll: AccessRule = rule(requiresAuthentication = false) { false }

    /** `hasRole('X')`, with exact comparison. For Spring's prefix, [RoleRules]. */
    fun hasRole(role: String): AccessRule = exactRoles.hasRole(role)

    /** `hasAnyRole('X', 'Y')`, with exact comparison. */
    fun hasAnyRole(vararg roles: String): AccessRule = exactRoles.hasAnyRole(*roles)

    /** Free rule over an already authenticated principal. */
    fun principal(test: (RestPrincipal) -> Boolean): AccessRule = rule { r -> r.principal?.let(test) == true }

    /** Free rule over the application's typed principal; a principal of another type is refused. */
    inline fun <reified P : RestPrincipal> principalOf(crossinline test: (P) -> Boolean): AccessRule =
        rule { r -> (r.principal as? P)?.let { test(it) } == true }

    /** Free rule over the request. */
    fun rule(requiresAuthentication: Boolean = true, test: (RequestContext) -> Boolean): AccessRule = object : AccessRule {
        override val requiresAuthentication: Boolean = requiresAuthentication
        override fun allows(request: RequestContext): Boolean = test(request)
    }
}
