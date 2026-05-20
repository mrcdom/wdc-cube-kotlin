package br.com.wdc.shopping.domain.repositories

import br.com.wdc.shopping.domain.criteria.UserCriteria
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.framework.commons.util.AtomicRef

interface UserRepository {

    fun insert(user: User): Boolean

    fun update(newUser: User, oldUser: User): Boolean

    fun insertOrUpdate(user: User): Boolean

    fun delete(criteria: UserCriteria): Int

    fun count(criteria: UserCriteria): Int

    fun fetch(criteria: UserCriteria): List<User>

    fun fetchPage(criteria: UserCriteria): Page<User>

    fun fetchById(userId: Long, projection: User?): User?

    companion object {
        val BEAN = AtomicRef<UserRepository>()
    }
}
