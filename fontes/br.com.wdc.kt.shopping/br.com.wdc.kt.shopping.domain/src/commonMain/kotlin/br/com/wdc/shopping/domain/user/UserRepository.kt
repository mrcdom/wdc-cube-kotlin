package br.com.wdc.shopping.domain.user

import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.domain.repository.Repository

interface UserRepository : Repository<User, UserCriteria, Long> {

    /** Todos os campos, **menos a senha** — quem precisa do resumo o pede explicitamente. */
    override fun newProjection(): User {
        val pv = ProjectionValues
        return User().apply {
            id = pv.i64
            userName = pv.str
            name = pv.str
            roles = pv.str
        }
    }

    override suspend fun fetchById(id: Long, projection: User?): User? =
        fetch(UserCriteria().withUserId(id).withProjection(projection ?: newProjection()), 0, 1).firstOrNull()

    companion object {
        val BEAN = AtomicRef<UserRepository>()
    }
}
