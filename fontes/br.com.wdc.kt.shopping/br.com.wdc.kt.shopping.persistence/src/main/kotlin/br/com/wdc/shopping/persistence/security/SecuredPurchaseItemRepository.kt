package br.com.wdc.shopping.persistence.security

import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.repositories.Page
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.security.SecurityContext

class SecuredPurchaseItemRepository(private val delegate: PurchaseItemRepository) : PurchaseItemRepository {

    companion object {
        private const val ENTITY = "purchase-item"
    }

    override suspend fun insert(purchaseItem: PurchaseItem): Boolean {
        SecurityEnforcer.require(ENTITY, "write")
        return delegate.insert(purchaseItem)
    }

    override suspend fun insertOrUpdate(purchaseItem: PurchaseItem): Boolean {
        SecurityEnforcer.require(ENTITY, "write")
        return delegate.insertOrUpdate(purchaseItem)
    }

    override suspend fun update(newPurchaseItem: PurchaseItem, oldPurchaseItem: PurchaseItem): Boolean {
        SecurityEnforcer.require(ENTITY, "write")
        return delegate.update(newPurchaseItem, oldPurchaseItem)
    }

    override suspend fun delete(criteria: PurchaseItemCriteria): Int {
        val sc = SecurityEnforcer.require(ENTITY, "delete")
        enforceUserScope(sc, criteria)
        return delegate.delete(criteria)
    }

    override suspend fun count(criteria: PurchaseItemCriteria): Int {
        val sc = SecurityEnforcer.require(ENTITY, "read")
        enforceUserScope(sc, criteria)
        return delegate.count(criteria)
    }

    override suspend fun fetch(criteria: PurchaseItemCriteria): List<PurchaseItem> {
        val sc = SecurityEnforcer.require(ENTITY, "read")
        enforceUserScope(sc, criteria)
        return delegate.fetch(criteria)
    }

    override suspend fun fetchPage(criteria: PurchaseItemCriteria): Page<PurchaseItem> {
        val sc = SecurityEnforcer.require(ENTITY, "read")
        enforceUserScope(sc, criteria)
        return delegate.fetchPage(criteria)
    }

    override suspend fun fetchById(purchaseId: Long, projection: PurchaseItem?): PurchaseItem? {
        SecurityEnforcer.require(ENTITY, "read")
        return delegate.fetchById(purchaseId, projection)
    }

    private fun enforceUserScope(sc: SecurityContext, criteria: PurchaseItemCriteria) {
        if (!sc.hasDataAll()) criteria.withUserId(sc.userId)
    }
}
