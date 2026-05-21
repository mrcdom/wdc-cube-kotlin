package br.com.wdc.shopping.domain.repositories

import br.com.wdc.shopping.domain.criteria.UserCriteria
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.framework.commons.util.AtomicRef

interface UserRepository {

    suspend fun insert(user: User): Boolean

    suspend fun update(newUser: User, oldUser: User): Boolean

    suspend fun insertOrUpdate(user: User): Boolean

    suspend fun delete(criteria: UserCriteria): Int

    suspend fun count(criteria: UserCriteria): Int

    suspend fun fetch(criteria: UserCriteria): List<User>

    suspend fun fetchPage(criteria: UserCriteria): Page<User>

    suspend fun fetchById(userId: Long, projection: User?): User?

    companion object {
        val BEAN = AtomicRef<UserRepository>()
    }
}
