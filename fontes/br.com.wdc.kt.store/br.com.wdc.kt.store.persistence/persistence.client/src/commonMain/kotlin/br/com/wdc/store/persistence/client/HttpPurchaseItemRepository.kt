package br.com.wdc.store.persistence.client

import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.store.domain.purchaseitem.PurchaseItem
import br.com.wdc.store.domain.purchaseitem.PurchaseItemCodec
import br.com.wdc.store.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.store.domain.purchaseitem.PurchaseItemRepository

class HttpPurchaseItemRepository(
    transport: HttpTransport,
    codec: ModelCodec<PurchaseItem, PurchaseItemCriteria> = PurchaseItemCodec(),
) : HttpRepository<PurchaseItem, PurchaseItemCriteria, Long>(transport, codec, "/api/repo/purchase-item"), PurchaseItemRepository {

    override fun fill(target: PurchaseItem, stored: PurchaseItem) = fillPurchaseItem(target, stored)
}

internal fun fillPurchaseItem(target: PurchaseItem, stored: PurchaseItem) {
    stored.id?.let { target.id = it }
    stored.amount?.let { target.amount = it }
    stored.price?.let { target.price = it }
    if (target.purchase?.id == null) stored.purchase?.let { target.purchase = it }
    if (target.product?.id == null) stored.product?.let { target.product = it }
}
