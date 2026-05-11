package br.com.wdc.shopping.domain.repositories

import br.com.wdc.shopping.domain.criteria.PurchaseCriteria
import br.com.wdc.shopping.domain.model.Purchase
import java.util.concurrent.atomic.AtomicReference

interface PurchaseRepository {

    fun insert(purchase: Purchase): Boolean

    fun insertOrUpdate(purchase: Purchase): Boolean

    fun update(newPurchase: Purchase, oldPurchase: Purchase): Boolean

    fun delete(criteria: PurchaseCriteria): Int

    fun count(criteria: PurchaseCriteria): Int

    fun fetch(criteria: PurchaseCriteria): List<Purchase>

    fun fetchById(purchaseId: Long, projection: Purchase?): Purchase?

    companion object {
        val BEAN = AtomicReference<PurchaseRepository>()
    }
}
