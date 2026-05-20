package br.com.wdc.shopping.domain.repositories

import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.framework.commons.util.AtomicRef

interface PurchaseItemRepository {

    fun insert(purchaseItem: PurchaseItem): Boolean

    fun insertOrUpdate(purchaseItem: PurchaseItem): Boolean

    fun update(newPurchaseItem: PurchaseItem, oldPurchaseItem: PurchaseItem): Boolean

    fun delete(criteria: PurchaseItemCriteria): Int

    fun count(criteria: PurchaseItemCriteria): Int

    fun fetch(criteria: PurchaseItemCriteria): List<PurchaseItem>

    fun fetchPage(criteria: PurchaseItemCriteria): Page<PurchaseItem>

    fun fetchById(purchaseId: Long, projection: PurchaseItem?): PurchaseItem?

    companion object {
        val BEAN = AtomicRef<PurchaseItemRepository>()
    }
}
