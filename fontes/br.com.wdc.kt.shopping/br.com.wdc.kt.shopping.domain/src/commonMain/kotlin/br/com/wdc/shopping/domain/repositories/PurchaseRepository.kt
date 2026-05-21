package br.com.wdc.shopping.domain.repositories

import br.com.wdc.shopping.domain.criteria.PurchaseCriteria
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.framework.commons.util.AtomicRef

interface PurchaseRepository {

    suspend fun insert(purchase: Purchase): Boolean

    suspend fun insertOrUpdate(purchase: Purchase): Boolean

    suspend fun update(newPurchase: Purchase, oldPurchase: Purchase): Boolean

    suspend fun delete(criteria: PurchaseCriteria): Int

    suspend fun count(criteria: PurchaseCriteria): Int

    suspend fun fetch(criteria: PurchaseCriteria): List<Purchase>

    suspend fun fetchPage(criteria: PurchaseCriteria): Page<Purchase>

    suspend fun fetchById(purchaseId: Long, projection: Purchase?): Purchase?

    companion object {
        val BEAN = AtomicRef<PurchaseRepository>()
    }
}
