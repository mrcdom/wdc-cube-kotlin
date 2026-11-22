package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.rest.RequestContext
import br.com.wdc.framework.rest.rpc.RepositoryRpc
import br.com.wdc.framework.rest.security.AccessRule
import br.com.wdc.framework.rest.security.AccessRules
import br.com.wdc.framework.rest.security.RestPrincipal
import br.com.wdc.shopping.domain.security.SecurityContext

/** The authenticated API caller: the [SecurityContext] the token resolves to. */
class ShoppingPrincipal(val context: SecurityContext) : RestPrincipal {
    override val id: String = context.userId.toString()
    override val roles: Set<String> get() = context.permissions
}

/**
 * Access control of the REST API: the caller's permission and the reach of their data.
 *
 * **Access is checked here, at the HTTP boundary** — and only here. The repositories check nothing: inside the
 * server their caller is the presentation layer, which the user cannot tamper with; callers from outside must
 * go through these routes.
 *
 * With security off (no authentication service registered), the API starts unsecured: the rules are not
 * consulted and there is no principal.
 */
internal object ApiSecurity {

    /** Requires the caller to be allowed to perform [operation] on [entity]. */
    fun permission(entity: String, operation: String): AccessRule =
        AccessRules.principalOf<ShoppingPrincipal> { it.context.hasPermission(entity, operation) }

    /** The permission each RPC operation requires: writes `write`, delete `delete`, queries `read`. */
    fun permission(entity: String, operation: RepositoryRpc.Operation): AccessRule = permission(
        entity,
        when (operation) {
            RepositoryRpc.Operation.INSERT, RepositoryRpc.Operation.UPDATE -> "write"
            RepositoryRpc.Operation.DELETE -> "delete"
            else -> "read"
        },
    )

    /** The caller's context, or `null` with security off. */
    fun context(request: RequestContext): SecurityContext? = request.principalAs<ShoppingPrincipal>()?.context

    /**
     * The user the caller's data is restricted to, or `null` if the caller reaches everyone's (`data:all`,
     * or security off).
     */
    fun ownerScope(request: RequestContext): Long? = context(request)?.takeIf { !it.hasDataAll() }?.userId
}
