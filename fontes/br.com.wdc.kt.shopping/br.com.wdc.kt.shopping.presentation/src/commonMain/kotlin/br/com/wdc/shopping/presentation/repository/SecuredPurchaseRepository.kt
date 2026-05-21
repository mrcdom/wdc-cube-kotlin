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

    override suspend fun insert(purchase: Purchase) =
        withSecurityContext(contextSupplier) { delegate.insert(purchase) }

    override suspend fun insertOrUpdate(purchase: Purchase) =
        withSecurityContext(contextSupplier) { delegate.insertOrUpdate(purchase) }

    override suspend fun update(newPurchase: Purchase, oldPurchase: Purchase) =
        withSecurityContext(contextSupplier) { delegate.update(newPurchase, oldPurchase) }

    override suspend fun delete(criteria: PurchaseCriteria) =
        withSecurityContext(contextSupplier) { delegate.delete(criteria) }

    override suspend fun count(criteria: PurchaseCriteria) =
        withSecurityContext(contextSupplier) { delegate.count(criteria) }

    override suspend fun fetch(criteria: PurchaseCriteria) =
        withSecurityContext(contextSupplier) { delegate.fetch(criteria) }

    override suspend fun fetchPage(criteria: PurchaseCriteria) =
        withSecurityContext(contextSupplier) { delegate.fetchPage(criteria) }

    override suspend fun fetchById(purchaseId: Long, projection: Purchase?) =
        withSecurityContext(contextSupplier) { delegate.fetchById(purchaseId, projection) }
}
