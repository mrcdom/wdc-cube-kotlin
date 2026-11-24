package br.com.wdc.store.persistence.rest

import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.rpc.RepositoryRpc
import br.com.wdc.store.domain.purchaseitem.PurchaseItem
import br.com.wdc.store.domain.purchaseitem.PurchaseItemCodec
import br.com.wdc.store.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.store.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.store.persistence.rest.doc.RepositoryApiDocs
import br.com.wdc.store.persistence.rest.security.PurchaseItemDataPolicy

/**
 * Purchase item REST endpoints: the repository RPC, read and written with the [PurchaseItemCodec] — the same one
 * the client uses. Access is decided by [PurchaseItemDataPolicy]. The price is the server's: the repository reads it
 * before the INSERT, and an update that touches the price or the product gets the product's current one ([ItemPrices]).
 */
internal object PurchaseItemApiController {

    private fun repo(): PurchaseItemRepository = PurchaseItemRepository.BEAN.get()

    fun register(routes: RestApi.Routes) {
        val rpc = RepositoryRpc<PurchaseItem, PurchaseItemCriteria, Long>(
            basePath = "/api/repo/purchase-item",
            codec = PurchaseItemCodec(),
            repository = ::repo,
            newCriteria = ::PurchaseItemCriteria,
            setProjection = { c, p -> c.withProjection(p) },
            restrictToId = { c, id -> c.withPurchaseItemId(id) },
            parseId = String::toLongOrNull,
            idOf = { it.id },
        )
        rpc.policy = PurchaseItemDataPolicy.POLICY
        rpc.doc = { op -> RepositoryApiDocs.rpcDoc("purchase-item", op) }
        // a transformation, after the policy's check
        rpc.beforeUpdate = { data, _ -> ItemPrices.applyTo(data) }
        rpc.register(routes)
    }
}
