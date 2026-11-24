package br.com.wdc.store.persistence.rest

import br.com.wdc.framework.rest.RequestContext
import br.com.wdc.framework.rest.security.RestPrincipal
import br.com.wdc.store.domain.security.SecurityContext

/** The authenticated API caller: the [SecurityContext] the token resolves to, with the names of the user's roles. */
class StorePrincipal(val context: SecurityContext) : RestPrincipal {
    override val id: String = context.userId.toString()
    override val roles: Set<String> get() = context.roles
}

/**
 * The caller of the REST API.
 *
 * **Access is checked at the HTTP boundary** — and only there. The repositories check nothing: inside the server their
 * caller is the presentation layer, which the user cannot tamper with; callers from outside must go through these
 * routes. What each role may do with the data is decided by the entity's data policy (`security.XxxDataPolicy`), set
 * on each repository RPC; the routes themselves only require an authenticated caller.
 *
 * With security off (no authentication service registered), the API starts unsecured: there is no principal, and the
 * policies reach every row and every field but the password.
 */
internal object ApiSecurity {

    /** The caller's context, or `null` with security off. */
    fun context(request: RequestContext): SecurityContext? = request.principalAs<StorePrincipal>()?.context
}
