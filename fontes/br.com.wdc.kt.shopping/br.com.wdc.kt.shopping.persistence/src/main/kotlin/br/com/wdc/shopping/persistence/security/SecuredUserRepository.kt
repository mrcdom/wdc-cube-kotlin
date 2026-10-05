package br.com.wdc.shopping.persistence.security

import br.com.wdc.shopping.domain.exception.AccessDeniedException
import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.domain.user.UserRepository

/**
 * Aplica o controle de acesso a usuário: permissão, escopo (quem não tem `data:all` só alcança o próprio
 * usuário) e a regra de que **a senha nunca sai por aqui**.
 *
 * `fetchById`, `fetchPage` e `insertOrUpdate` não são sobrescritos de propósito: os defaults da interface
 * passam por `fetch`, `count`, `insert` e `update`, que já são verificados.
 */
class SecuredUserRepository(private val delegate: UserRepository) : UserRepository {

    companion object {
        private const val ENTITY = "user"
    }

    override fun newProjection(): User = delegate.newProjection()

    override suspend fun insert(bean: User): Boolean {
        SecurityEnforcer.require(ENTITY, "write")
        return delegate.insert(bean)
    }

    override suspend fun update(newBean: User, oldBean: User?, projection: User?): Boolean {
        val sc = SecurityEnforcer.require(ENTITY, "write")
        if (!sc.hasDataAll() && newBean.id != null && newBean.id != sc.userId) {
            throw AccessDeniedException("Cannot modify other user's data")
        }
        return delegate.update(newBean, oldBean, projection)
    }

    override suspend fun delete(criteria: UserCriteria): Int {
        val sc = SecurityEnforcer.require(ENTITY, "delete")
        enforceUserScope(sc, criteria)
        return delegate.delete(criteria)
    }

    override suspend fun count(criteria: UserCriteria): Int {
        val sc = SecurityEnforcer.require(ENTITY, "read")
        enforceUserScope(sc, criteria)
        return delegate.count(criteria)
    }

    override suspend fun fetch(criteria: UserCriteria, offset: Int, limit: Int): List<User> {
        val sc = SecurityEnforcer.require(ENTITY, "read")
        enforceUserScope(sc, criteria)
        criteria.projection?.password = null
        val results = delegate.fetch(criteria, offset, limit)
        results.forEach { it.password = null }
        return results
    }

    /** Os pedidos de um campo acumulam em `AND`: o que o chamador pediu continua valendo, restrito ao próprio usuário. */
    private fun enforceUserScope(sc: SecurityContext, criteria: UserCriteria) {
        if (!sc.hasDataAll()) criteria.userId.eq(sc.userId)
    }
}
