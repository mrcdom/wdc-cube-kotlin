package br.com.wdc.shopping.presentation.repository

import br.com.wdc.shopping.domain.criteria.UserCriteria
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.repositories.Page
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.presentation.util.withSecurityContext

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

    override fun fetchPage(criteria: UserCriteria) =
        withSecurityContext(contextSupplier) { delegate.fetchPage(criteria) }

    override fun fetchById(userId: Long, projection: User?) =
        withSecurityContext(contextSupplier) { delegate.fetchById(userId, projection) }
}
