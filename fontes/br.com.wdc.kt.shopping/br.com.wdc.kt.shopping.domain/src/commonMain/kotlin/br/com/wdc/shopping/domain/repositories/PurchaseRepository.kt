package br.com.wdc.shopping.domain.repositories

import br.com.wdc.shopping.domain.criteria.PurchaseCriteria
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.framework.commons.util.AtomicRef

interface PurchaseRepository {

    fun insert(purchase: Purchase): Boolean

    fun insertOrUpdate(purchase: Purchase): Boolean

    fun update(newPurchase: Purchase, oldPurchase: Purchase): Boolean

    fun delete(criteria: PurchaseCriteria): Int

    fun count(criteria: PurchaseCriteria): Int

    fun fetch(criteria: PurchaseCriteria): List<Purchase>

    fun fetchPage(criteria: PurchaseCriteria): Page<Purchase>

    fun fetchById(purchaseId: Long, projection: Purchase?): Purchase?

    companion object {
        val BEAN = AtomicRef<PurchaseRepository>()
    }
}
