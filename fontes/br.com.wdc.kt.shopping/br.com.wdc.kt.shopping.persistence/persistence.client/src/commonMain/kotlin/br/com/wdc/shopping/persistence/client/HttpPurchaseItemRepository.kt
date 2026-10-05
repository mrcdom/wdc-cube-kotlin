package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCodec
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemRepository

class HttpPurchaseItemRepository(
    transport: HttpTransport,
    codec: ModelCodec<PurchaseItem, PurchaseItemCriteria> = PurchaseItemCodec(),
) : HttpRepository<PurchaseItem, PurchaseItemCriteria, Long>(transport, codec, "/api/repo/purchase-item"), PurchaseItemRepository
