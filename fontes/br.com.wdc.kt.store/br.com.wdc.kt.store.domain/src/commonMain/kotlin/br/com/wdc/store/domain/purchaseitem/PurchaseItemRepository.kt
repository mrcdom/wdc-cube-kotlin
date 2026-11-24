package br.com.wdc.store.domain.purchaseitem

import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.domain.repository.Repository
import br.com.wdc.store.domain.product.Product
import br.com.wdc.store.domain.purchase.Purchase

interface PurchaseItemRepository : Repository<PurchaseItem, PurchaseItemCriteria, Long> {

    /** The item and the purchase and product ids. */
    override fun ownFieldsProjection(): PurchaseItem {
        val pv = ProjectionValues
        return PurchaseItem().apply {
            id = pv.i64
            amount = pv.i32
            price = pv.f64
            purchase = Purchase().apply { id = pv.i64 }
            product = Product().apply { id = pv.i64 }
        }
    }

    override suspend fun fetchById(id: Long, projection: PurchaseItem?): PurchaseItem? =
        fetch(PurchaseItemCriteria().withPurchaseItemId(id).withProjection(projection ?: ownFieldsProjection()), 0, 1).firstOrNull()

    companion object {
        val BEAN = AtomicRef<PurchaseItemRepository>()
    }
}
