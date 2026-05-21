package br.com.wdc.shopping.domain.repositories

import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.framework.commons.util.AtomicRef

interface PurchaseItemRepository {

    suspend fun insert(purchaseItem: PurchaseItem): Boolean

    suspend fun insertOrUpdate(purchaseItem: PurchaseItem): Boolean

    suspend fun update(newPurchaseItem: PurchaseItem, oldPurchaseItem: PurchaseItem): Boolean

    suspend fun delete(criteria: PurchaseItemCriteria): Int

    suspend fun count(criteria: PurchaseItemCriteria): Int

    suspend fun fetch(criteria: PurchaseItemCriteria): List<PurchaseItem>

    suspend fun fetchPage(criteria: PurchaseItemCriteria): Page<PurchaseItem>

    suspend fun fetchById(purchaseId: Long, projection: PurchaseItem?): PurchaseItem?

    companion object {
        val BEAN = AtomicRef<PurchaseItemRepository>()
    }
}
