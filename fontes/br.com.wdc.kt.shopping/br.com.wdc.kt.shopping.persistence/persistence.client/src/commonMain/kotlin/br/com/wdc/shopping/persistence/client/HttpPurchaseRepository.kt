package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseCodec
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchase.PurchaseRepository

class HttpPurchaseRepository(
    transport: HttpTransport,
    codec: ModelCodec<Purchase, PurchaseCriteria> = PurchaseCodec(),
) : HttpRepository<Purchase, PurchaseCriteria, Long>(transport, codec, "/api/repo/purchase"), PurchaseRepository {

    override fun fill(target: Purchase, stored: Purchase) = fillPurchase(target, stored)
}

/** Os itens são completados pela posição: o servidor os devolve na ordem em que vieram. */
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
