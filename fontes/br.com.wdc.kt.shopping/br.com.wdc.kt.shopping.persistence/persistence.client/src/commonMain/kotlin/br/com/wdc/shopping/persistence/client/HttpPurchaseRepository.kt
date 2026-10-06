package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseCodec
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchase.PurchaseRepository

class HttpPurchaseRepository(
    transport: HttpTransport,
    codec: ModelCodec<Purchase, PurchaseCriteria> = PurchaseCodec(),
) : HttpRepository<Purchase, PurchaseCriteria, Long>(transport, codec, "/api/repo/purchase"), PurchaseRepository
