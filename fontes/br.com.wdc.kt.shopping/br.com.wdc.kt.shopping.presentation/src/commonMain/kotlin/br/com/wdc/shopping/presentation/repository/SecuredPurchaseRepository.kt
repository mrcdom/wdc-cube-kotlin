package br.com.wdc.shopping.presentation.repository

import br.com.wdc.shopping.domain.criteria.PurchaseCriteria
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.repositories.Page
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.presentation.util.withSecurityContext

class SecuredPurchaseRepository(
    private val delegate: PurchaseRepository,
    private val contextSupplier: () -> SecurityContext?,
) : PurchaseRepository {

    override fun insert(purchase: Purchase) =
        withSecurityContext(contextSupplier) { delegate.insert(purchase) }

    override fun insertOrUpdate(purchase: Purchase) =
        withSecurityContext(contextSupplier) { delegate.insertOrUpdate(purchase) }

    override fun update(newPurchase: Purchase, oldPurchase: Purchase) =
        withSecurityContext(contextSupplier) { delegate.update(newPurchase, oldPurchase) }

    override fun delete(criteria: PurchaseCriteria) =
        withSecurityContext(contextSupplier) { delegate.delete(criteria) }

    override fun count(criteria: PurchaseCriteria) =
        withSecurityContext(contextSupplier) { delegate.count(criteria) }

    override fun fetch(criteria: PurchaseCriteria) =
        withSecurityContext(contextSupplier) { delegate.fetch(criteria) }

    override fun fetchPage(criteria: PurchaseCriteria) =
        withSecurityContext(contextSupplier) { delegate.fetchPage(criteria) }

    override fun fetchById(purchaseId: Long, projection: Purchase?) =
        withSecurityContext(contextSupplier) { delegate.fetchById(purchaseId, projection) }
}
