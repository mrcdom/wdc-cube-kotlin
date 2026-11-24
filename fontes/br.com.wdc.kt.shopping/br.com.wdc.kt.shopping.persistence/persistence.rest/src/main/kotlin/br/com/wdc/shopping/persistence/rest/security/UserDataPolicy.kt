package br.com.wdc.shopping.persistence.rest.security

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.rest.policy.DataPolicy
import br.com.wdc.framework.rest.policy.EntityDescriptor
import br.com.wdc.framework.rest.policy.Mask
import br.com.wdc.framework.rest.policy.RowScope
import br.com.wdc.framework.rest.security.RestPrincipal
import br.com.wdc.shopping.domain.security.Role
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.domain.user.UserCriteria

/**
 * Who may do what with the users through the REST API.
 *
 * - **ADMIN** reads every user, creates, updates and deletes them.
 * - **CUSTOMER** reads its own user.
 * - **MANAGER** reads its own user in full, and the others by name, with the login masked.
 *
 * **The password is write-only**: it is written when a user is created or updated, and never read back — it is in no
 * read grant, so asking for it is refused (403, naming `password`), even with security off.
 */
object UserDataPolicy {

    private val PV = ProjectionValues

    val USER: EntityDescriptor<User, UserCriteria> = EntityDescriptor("user", ::User, ::UserCriteria) {
        projection(UserCriteria::projection, UserCriteria::withProjection)
        anyOf(UserCriteria::anyOf, UserCriteria::withAnyOf)
        key(User::id, PV.i64)
        value(User::userName, PV.str)
        writeOnly(User::password, PV.str)
        value(User::name, PV.str)
        value(User::roles, PV.str)
        criterion("userId", User::id)
        orderings { c ->
            when (c.orderBy) {
                null, UserCriteria.OrderBy.OLDEST_FIRST, UserCriteria.OrderBy.NEWEST_FIRST -> emptySet()
                UserCriteria.OrderBy.NAME_A_TO_Z -> setOf(User::name)
                UserCriteria.OrderBy.LOGIN_A_TO_Z -> setOf(User::userName)
            }
        }
    }

    /** The requester's own user. */
    object OWN : RowScope<User, UserCriteria> {
        override fun restrict(group: UserCriteria, requester: RestPrincipal) {
            group.userId.eq(requester.userId())
        }

        override suspend fun covers(row: User, requester: RestPrincipal): Boolean = row.id == requester.userId()

        override fun toString(): String = "OWN"
    }

    val POLICY: DataPolicy<User, UserCriteria> = DataPolicy(USER) {
        grant(Role.ADMIN.name) {
            read(User::userName, User::name, User::roles)
            create(User::userName, User::password, User::name, User::roles)
            update(User::userName, User::password, User::name, User::roles)
            delete()
        }
        // what a user reads of their own record: the signed-in user is shown by the client
        grant(Role.CUSTOMER.name, Role.MANAGER.name) {
            rows(OWN)
            read(User::userName, User::name, User::roles)
        }
        grant(Role.MANAGER.name) {
            read(User::name)
            mask(Mask.keepFirst(1), User::userName)
        }
    }
}
