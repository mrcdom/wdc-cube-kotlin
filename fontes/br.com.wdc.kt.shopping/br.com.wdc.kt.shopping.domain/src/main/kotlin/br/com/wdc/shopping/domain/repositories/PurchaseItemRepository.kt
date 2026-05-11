package br.com.wdc.shopping.domain.repositories

import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.domain.model.PurchaseItem
import java.util.concurrent.atomic.AtomicReference

interface PurchaseItemRepository {

    fun insert(purchaseItem: PurchaseItem): Boolean

    fun insertOrUpdate(purchaseItem: PurchaseItem): Boolean

    fun update(newPurchaseItem: PurchaseItem, oldPurchaseItem: PurchaseItem): Boolean

    fun delete(criteria: PurchaseItemCriteria): Int

    fun count(criteria: PurchaseItemCriteria): Int

    fun fetch(criteria: PurchaseItemCriteria): List<PurchaseItem>

    fun fetchById(purchaseId: Long, projection: PurchaseItem?): PurchaseItem?

    companion object {
        val BEAN = AtomicReference<PurchaseItemRepository>()
    }
}
