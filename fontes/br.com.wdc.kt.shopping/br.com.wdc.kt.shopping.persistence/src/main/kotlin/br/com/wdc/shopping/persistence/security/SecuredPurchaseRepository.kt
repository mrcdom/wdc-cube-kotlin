package br.com.wdc.shopping.persistence.security

import br.com.wdc.shopping.domain.criteria.PurchaseCriteria
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.security.SecurityContext

class SecuredPurchaseRepository(private val delegate: PurchaseRepository) : PurchaseRepository {

    companion object {
        private const val ENTITY = "purchase"
    }

    override fun insert(purchase: Purchase): Boolean {
        val sc = SecurityEnforcer.require(ENTITY, "write")
        enforceUserScope(sc, purchase)
        return delegate.insert(purchase)
    }

    override fun insertOrUpdate(purchase: Purchase): Boolean {
        val sc = SecurityEnforcer.require(ENTITY, "write")
        enforceUserScope(sc, purchase)
        return delegate.insertOrUpdate(purchase)
    }

    override fun update(newPurchase: Purchase, oldPurchase: Purchase): Boolean {
        SecurityEnforcer.require(ENTITY, "write")
        return delegate.update(newPurchase, oldPurchase)
    }

    override fun delete(criteria: PurchaseCriteria): Int {
        val sc = SecurityEnforcer.require(ENTITY, "delete")
        enforceUserScope(sc, criteria)
        return delegate.delete(criteria)
    }

    override fun count(criteria: PurchaseCriteria): Int {
        val sc = SecurityEnforcer.require(ENTITY, "read")
        enforceUserScope(sc, criteria)
        return delegate.count(criteria)
    }

    override fun fetch(criteria: PurchaseCriteria): List<Purchase> {
        val sc = SecurityEnforcer.require(ENTITY, "read")
        enforceUserScope(sc, criteria)
        return delegate.fetch(criteria)
    }

    override fun fetchById(purchaseId: Long, projection: Purchase?): Purchase? {
        val sc = SecurityEnforcer.require(ENTITY, "read")
        val result = delegate.fetchById(purchaseId, projection)
        if (result != null && !sc.hasDataAll()
            && result.user != null && sc.userId != result.user!!.id) {
            return null
        }
        return result
    }

    private fun enforceUserScope(sc: SecurityContext, criteria: PurchaseCriteria) {
        if (!sc.hasDataAll()) criteria.withUserId(sc.userId)
    }

    private fun enforceUserScope(sc: SecurityContext, purchase: Purchase) {
        if (!sc.hasDataAll()) {
            if (purchase.user == null) purchase.user = User()
            purchase.user!!.id = sc.userId
        }
    }
}
