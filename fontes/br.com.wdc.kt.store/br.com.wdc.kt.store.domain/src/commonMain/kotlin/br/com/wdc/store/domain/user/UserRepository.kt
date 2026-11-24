package br.com.wdc.store.domain.user

import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.domain.repository.Repository

interface UserRepository : Repository<User, UserCriteria, Long> {

    /** All fields **except the password** — whoever needs the digest asks for it explicitly. */
    override fun ownFieldsProjection(): User {
        val pv = ProjectionValues
        return User().apply {
            id = pv.i64
            userName = pv.str
            name = pv.str
            roles = pv.str
        }
    }

    override suspend fun fetchById(id: Long, projection: User?): User? =
        fetch(UserCriteria().withUserId(id).withProjection(projection ?: ownFieldsProjection()), 0, 1).firstOrNull()

    companion object {
        val BEAN = AtomicRef<UserRepository>()
    }
}
