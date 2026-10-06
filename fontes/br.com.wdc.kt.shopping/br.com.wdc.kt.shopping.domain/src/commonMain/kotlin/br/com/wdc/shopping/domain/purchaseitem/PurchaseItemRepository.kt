package br.com.wdc.shopping.domain.purchaseitem

import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.framework.domain.repository.Repository
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.purchase.Purchase

interface PurchaseItemRepository : Repository<PurchaseItem, PurchaseItemCriteria, Long> {

    /** O item e os ids da compra e do produto. */
    override fun newProjection(): PurchaseItem {
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
        fetch(PurchaseItemCriteria().withPurchaseItemId(id).withProjection(projection ?: newProjection()), 0, 1).firstOrNull()

    companion object {
        val BEAN = AtomicRef<PurchaseItemRepository>()
    }
}
