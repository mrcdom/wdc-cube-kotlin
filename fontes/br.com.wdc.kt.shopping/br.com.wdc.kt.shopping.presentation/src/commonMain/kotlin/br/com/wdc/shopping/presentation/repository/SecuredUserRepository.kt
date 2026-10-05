package br.com.wdc.shopping.presentation.repository

import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.domain.user.UserRepository
import br.com.wdc.shopping.presentation.util.withSecurityContext

/**
 * Executa as operações de usuário com o contexto de segurança da aplicação. `fetchById`, `fetchPage` e
 * `insertOrUpdate` vêm dos defaults da interface, que passam pelas operações abaixo.
 */
class SecuredUserRepository(
    private val delegate: UserRepository,
    private val contextSupplier: () -> SecurityContext?,
) : UserRepository {

    override fun newProjection(): User = delegate.newProjection()

    override suspend fun insert(bean: User) =
        withSecurityContext(contextSupplier) { delegate.insert(bean) }

    override suspend fun update(newBean: User, oldBean: User?, projection: User?) =
        withSecurityContext(contextSupplier) { delegate.update(newBean, oldBean, projection) }

    override suspend fun delete(criteria: UserCriteria) =
        withSecurityContext(contextSupplier) { delegate.delete(criteria) }

    override suspend fun count(criteria: UserCriteria) =
        withSecurityContext(contextSupplier) { delegate.count(criteria) }

    override suspend fun fetch(criteria: UserCriteria, offset: Int, limit: Int) =
        withSecurityContext(contextSupplier) { delegate.fetch(criteria, offset, limit) }
}
