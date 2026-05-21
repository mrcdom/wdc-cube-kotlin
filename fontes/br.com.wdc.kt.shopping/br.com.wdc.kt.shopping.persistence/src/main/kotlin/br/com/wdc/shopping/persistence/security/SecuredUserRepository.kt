package br.com.wdc.shopping.persistence.security

import br.com.wdc.shopping.domain.criteria.UserCriteria
import br.com.wdc.shopping.domain.exception.AccessDeniedException
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.repositories.Page
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.domain.security.SecurityContext

class SecuredUserRepository(private val delegate: UserRepository) : UserRepository {

    companion object {
        private const val ENTITY = "user"
    }

    override suspend fun insert(user: User): Boolean {
        SecurityEnforcer.require(ENTITY, "write")
        return delegate.insert(user)
    }

    override suspend fun update(newUser: User, oldUser: User): Boolean {
        val sc = SecurityEnforcer.require(ENTITY, "write")
        enforceUserScope(sc, newUser)
        return delegate.update(newUser, oldUser)
    }

    override suspend fun insertOrUpdate(user: User): Boolean {
        SecurityEnforcer.require(ENTITY, "write")
        return delegate.insertOrUpdate(user)
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

    override suspend fun fetch(criteria: UserCriteria): List<User> {
        val sc = SecurityEnforcer.require(ENTITY, "read")
        enforceUserScope(sc, criteria)
        sanitizeProjection(criteria)
        val results = delegate.fetch(criteria)
        results.forEach { it.password = null }
        return results
    }

    override suspend fun fetchPage(criteria: UserCriteria): Page<User> {
        val sc = SecurityEnforcer.require(ENTITY, "read")
        enforceUserScope(sc, criteria)
        sanitizeProjection(criteria)
        val page = delegate.fetchPage(criteria)
        page.items.forEach { it.password = null }
        return page
    }

    override suspend fun fetchById(userId: Long, projection: User?): User? {
        val sc = SecurityEnforcer.require(ENTITY, "read")
        if (!sc.hasDataAll() && userId != sc.userId) return null
        stripPassword(projection)
        val result = delegate.fetchById(userId, projection)
        result?.let { it.password = null }
        return result
    }

    private fun enforceUserScope(sc: SecurityContext, criteria: UserCriteria) {
        if (!sc.hasDataAll()) criteria.withUserId(sc.userId)
    }

    private fun enforceUserScope(sc: SecurityContext, user: User) {
        if (!sc.hasDataAll() && user.id != null && user.id != sc.userId) {
            throw AccessDeniedException("Cannot modify other user's data")
        }
    }

    private fun sanitizeProjection(criteria: UserCriteria) {
        criteria.projection?.let { it.password = null }
    }

    private fun stripPassword(projection: User?) {
        projection?.let { it.password = null }
    }
}
