package br.com.wdc.store.persistence.client

import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.store.domain.purchase.Purchase
import br.com.wdc.store.domain.purchase.PurchaseCodec
import br.com.wdc.store.domain.purchase.PurchaseCriteria
import br.com.wdc.store.domain.purchase.PurchaseRepository

class HttpPurchaseRepository(
    transport: HttpTransport,
    codec: ModelCodec<Purchase, PurchaseCriteria> = PurchaseCodec(),
) : HttpRepository<Purchase, PurchaseCriteria, Long>(transport, codec, "/api/repo/purchase"), PurchaseRepository {

    override fun fill(target: Purchase, stored: Purchase) = fillPurchase(target, stored)
}

/** Items are filled by position: the server returns them in the order they were sent. */
internal fun fillPurchase(target: Purchase, stored: Purchase) {
    stored.id?.let { target.id = it }
    stored.buyDate?.let { target.buyDate = it }
    if (target.user?.id == null) stored.user?.let { target.user = it }
    val targetItems = target.items
    val storedItems = stored.items
    if (targetItems != null && storedItems != null && targetItems.size == storedItems.size) {
        targetItems.zip(storedItems).forEach { (item, storedItem) -> fillPurchaseItem(item, storedItem) }
    }
}
