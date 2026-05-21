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

    override suspend fun insert(user: User) =
        withSecurityContext(contextSupplier) { delegate.insert(user) }

    override suspend fun update(newUser: User, oldUser: User) =
        withSecurityContext(contextSupplier) { delegate.update(newUser, oldUser) }

    override suspend fun insertOrUpdate(user: User) =
        withSecurityContext(contextSupplier) { delegate.insertOrUpdate(user) }

    override suspend fun delete(criteria: UserCriteria) =
        withSecurityContext(contextSupplier) { delegate.delete(criteria) }

    override suspend fun count(criteria: UserCriteria) =
        withSecurityContext(contextSupplier) { delegate.count(criteria) }

    override suspend fun fetch(criteria: UserCriteria) =
        withSecurityContext(contextSupplier) { delegate.fetch(criteria) }

    override suspend fun fetchPage(criteria: UserCriteria) =
        withSecurityContext(contextSupplier) { delegate.fetchPage(criteria) }

    override suspend fun fetchById(userId: Long, projection: User?) =
        withSecurityContext(contextSupplier) { delegate.fetchById(userId, projection) }
}
