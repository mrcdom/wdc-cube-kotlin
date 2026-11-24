package br.com.wdc.framework.rest.policy

import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.rest.security.RestPrincipal

/**
 * Who may do what with the data of an entity through an API open to external clients: per role, the rows it reaches,
 * the fields it reads (as they are or masked), the fields it writes, and whether it deletes.
 *
 * It exists for the boundary where the request is assembled by the client — the repository RPC
 * ([br.com.wdc.framework.rest.rpc.RepositoryRpc.policy]), which accepts any criteria and any projection. Code that runs
 * on the server chooses its own queries and uses the repositories directly, without a policy.
 *
 * The model is **allow-wins**: a requester with several roles gets every grant of every role ([RestPrincipal.roles]),
 * and a request is allowed when some grant allows it. Fields are decided **per row**: a row shows what the grants that
 * reach it allow, so reading everyone's name in one role and one's own document in another does not add up to
 * everyone's document.
 *
 * ```
 * val POLICY = DataPolicy(USER) {
 *     grant("ADMIN") {
 *         read(User::userName, User::name)
 *         update(User::name)
 *         delete()
 *     }
 *     grant("CUSTOMER") {
 *         rows(OwnUser)
 *         read(User::userName, User::name)
 *     }
 * }
 * ```
 *
 * @param E the entity
 * @param C its criteria
 */
class DataPolicy<E : Any, C : Criteria>(val entity: EntityDescriptor<E, C>, declare: Builder<E, C>.() -> Unit) {

    private val grantsByRole: Map<String, List<DataGrant<E, C>>>

    init {
        grantsByRole = Builder(entity).apply(declare).grantsByRole.mapValues { it.value.toList() }
    }

    /** The grants of a role; empty when the role has none on this entity. */
    fun grantsOf(role: String): List<DataGrant<E, C>> = grantsByRole[role] ?: emptyList()

    /**
     * What the requester may do. Without a requester — security is off, in local or test mode — every row and every
     * field is reached and every write passes, but for the write-only fields ([EntityDescriptor.Builder.writeOnly]),
     * which nobody reads; a read without a projection keeps the repository's own default.
     */
    fun accessFor(requester: RestPrincipal?): DataAccess<E, C> {
        if (requester == null) return DataAccess(entity, null, listOf(DataGrant.unrestricted(entity)))
        val grants = ArrayList<DataGrant<E, C>>()
        for (role in requester.roles) {
            // a grant shared by two roles counts once
            for (grant in grantsOf(role)) if (grants.none { it === grant }) grants.add(grant)
        }
        return DataAccess(entity, requester, grants)
    }

    class Builder<E : Any, C : Criteria> internal constructor(private val entity: EntityDescriptor<E, C>) {
        internal val grantsByRole = LinkedHashMap<String, MutableList<DataGrant<E, C>>>()

        /** Adds a grant to each of [roles]; a role may have several, one per set of rows. */
        fun grant(vararg roles: String, declare: DataGrant.Builder<E, C>.() -> Unit) {
            require(roles.isNotEmpty()) { "${entity.name}: a grant needs a role" }
            val grant = DataGrant.Builder(entity).apply(declare).build()
            for (role in roles) grantsByRole.getOrPut(role) { ArrayList() }.add(grant)
        }
    }
}
