package br.com.wdc.shopping.presentation.repository

import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.repositories.Page
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.presentation.util.withSecurityContext

class SecuredPurchaseItemRepository(
    private val delegate: PurchaseItemRepository,
    private val contextSupplier: () -> SecurityContext?,
) : PurchaseItemRepository {

    override fun insert(purchaseItem: PurchaseItem) =
        withSecurityContext(contextSupplier) { delegate.insert(purchaseItem) }

    override fun insertOrUpdate(purchaseItem: PurchaseItem) =
        withSecurityContext(contextSupplier) { delegate.insertOrUpdate(purchaseItem) }

    override fun update(newPurchaseItem: PurchaseItem, oldPurchaseItem: PurchaseItem) =
        withSecurityContext(contextSupplier) { delegate.update(newPurchaseItem, oldPurchaseItem) }

    override fun delete(criteria: PurchaseItemCriteria) =
        withSecurityContext(contextSupplier) { delegate.delete(criteria) }

    override fun count(criteria: PurchaseItemCriteria) =
        withSecurityContext(contextSupplier) { delegate.count(criteria) }

    override fun fetch(criteria: PurchaseItemCriteria) =
        withSecurityContext(contextSupplier) { delegate.fetch(criteria) }

    override fun fetchPage(criteria: PurchaseItemCriteria) =
        withSecurityContext(contextSupplier) { delegate.fetchPage(criteria) }

    override fun fetchById(purchaseId: Long, projection: PurchaseItem?) =
        withSecurityContext(contextSupplier) { delegate.fetchById(purchaseId, projection) }
}
