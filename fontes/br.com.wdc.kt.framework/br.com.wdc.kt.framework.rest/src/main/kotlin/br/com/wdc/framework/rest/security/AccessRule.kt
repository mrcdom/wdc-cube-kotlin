package br.com.wdc.framework.rest.security

import br.com.wdc.framework.rest.RequestContext

/**
 * Regra de acesso de uma rota — o equivalente ao `@PreAuthorize`. Decide com o que já se sabe da requisição:
 * o principal, os parâmetros de caminho e de query, os cabeçalhos. Regra que depende do corpo ou de dados do
 * banco fica no handler, que lança `AccessDeniedException`.
 *
 * [requiresAuthentication] diz se o anônimo é recusado como "sem credencial" antes de a regra ser consultada —
 * os dois casos podem ter status e corpo diferentes ([SecurityConfig]).
 */
interface AccessRule {

    /** `true` se a rota exige principal. O anônimo recebe a resposta de "sem credencial", e não a de "proibido". */
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
 * Regras de papel com um prefixo, como o Spring Security: `hasRole("ADMIN")` e `hasRole("ROLE_ADMIN")` aceitam
 * o papel `ROLE_ADMIN` quando o prefixo é `ROLE_`. Com prefixo vazio, a comparação é exata.
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

/** As regras de uso comum. */
object AccessRules {

    private val exactRoles = RoleRules("")

    /** Qualquer um, autenticado ou não (`permitAll`). */
    val permitAll: AccessRule = rule(requiresAuthentication = false) { true }

    /** Qualquer autenticado (`isAuthenticated()`). */
    val authenticated: AccessRule = rule { it.principal != null }

    /** Ninguém (`denyAll`). */
    val denyAll: AccessRule = rule(requiresAuthentication = false) { false }

    /** `hasRole('X')`, com comparação exata. Para o prefixo do Spring, [RoleRules]. */
    fun hasRole(role: String): AccessRule = exactRoles.hasRole(role)

    /** `hasAnyRole('X', 'Y')`, com comparação exata. */
    fun hasAnyRole(vararg roles: String): AccessRule = exactRoles.hasAnyRole(*roles)

    /** Regra livre sobre um principal já autenticado. */
    fun principal(test: (RestPrincipal) -> Boolean): AccessRule = rule { r -> r.principal?.let(test) == true }

    /** Regra livre sobre o principal tipado da aplicação; principal de outro tipo é recusado. */
    inline fun <reified P : RestPrincipal> principalOf(crossinline test: (P) -> Boolean): AccessRule =
        rule { r -> (r.principal as? P)?.let { test(it) } == true }

    /** Regra livre sobre a requisição. */
    fun rule(requiresAuthentication: Boolean = true, test: (RequestContext) -> Boolean): AccessRule = object : AccessRule {
        override val requiresAuthentication: Boolean = requiresAuthentication
        override fun allows(request: RequestContext): Boolean = test(request)
    }
}
