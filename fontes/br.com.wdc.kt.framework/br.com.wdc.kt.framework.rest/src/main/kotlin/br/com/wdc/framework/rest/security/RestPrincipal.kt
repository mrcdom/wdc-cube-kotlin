package br.com.wdc.framework.rest.security

/**
 * The authenticated caller. The application implements it with what its token carries (user, company, keys…) and
 * reads it back with `RequestContext.principalAs<YourType>()`.
 */
interface RestPrincipal {

    /** Stable identifier of the caller. It is what distinguishes the owner of a remote transaction. */
    val id: String

    /** The roles, as the application writes them (`ADMIN`, `ROLE_ADMIN`…): the library only compares strings. */
    val roles: Set<String>

    fun hasRole(role: String): Boolean = role in roles

    fun hasAnyRole(vararg roles: String): Boolean = roles.any { it in this.roles }
}

/** Simple principal, for those who need nothing more than the id and the roles. */
data class SimplePrincipal(override val id: String, override val roles: Set<String> = emptySet()) : RestPrincipal
