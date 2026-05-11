package br.com.wdc.shopping.presentation

import br.com.wdc.shopping.domain.criteria.UserCriteria
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.domain.security.SecurityContext

class SecuredUserRepository(
    private val delegate: UserRepository,
    private val contextSupplier: () -> SecurityContext?,
) : UserRepository {

    override fun insert(user: User) =
        withSecurityContext(contextSupplier) { delegate.insert(user) }

    override fun update(newUser: User, oldUser: User) =
        withSecurityContext(contextSupplier) { delegate.update(newUser, oldUser) }

    override fun insertOrUpdate(user: User) =
        withSecurityContext(contextSupplier) { delegate.insertOrUpdate(user) }

    override fun delete(criteria: UserCriteria) =
        withSecurityContext(contextSupplier) { delegate.delete(criteria) }

    override fun count(criteria: UserCriteria) =
        withSecurityContext(contextSupplier) { delegate.count(criteria) }

    override fun fetch(criteria: UserCriteria) =
        withSecurityContext(contextSupplier) { delegate.fetch(criteria) }

    override fun fetchById(userId: Long, projection: User?) =
        withSecurityContext(contextSupplier) { delegate.fetchById(userId, projection) }
}
