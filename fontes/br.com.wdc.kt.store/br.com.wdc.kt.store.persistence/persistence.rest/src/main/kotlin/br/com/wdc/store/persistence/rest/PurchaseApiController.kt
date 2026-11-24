package br.com.wdc.store.persistence.rest

import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.rpc.RepositoryRpc
import br.com.wdc.store.domain.purchase.Purchase
import br.com.wdc.store.domain.purchase.PurchaseCodec
import br.com.wdc.store.domain.purchase.PurchaseCriteria
import br.com.wdc.store.domain.purchase.PurchaseRepository
import br.com.wdc.store.persistence.rest.doc.RepositoryApiDocs
import br.com.wdc.store.persistence.rest.security.PurchaseDataPolicy

/**
 * Purchase REST endpoints: the repository RPC, read and written with the [PurchaseCodec] — the same one the
 * client uses. Access is decided by [PurchaseDataPolicy]: a purchase is a receipt, which its buyer creates and reads,
 * and only an administrator changes. A read without a projection gets the receipt (date, buyer's name, items).
 *
 * The items that come with a new purchase are priced by the repository, before the INSERT.
 */
internal object PurchaseApiController {

    private fun repo(): PurchaseRepository = PurchaseRepository.BEAN.get()

    fun register(routes: RestApi.Routes) {
        val rpc = RepositoryRpc<Purchase, PurchaseCriteria, Long>(
            basePath = "/api/repo/purchase",
            codec = PurchaseCodec(),
            repository = ::repo,
            newCriteria = ::PurchaseCriteria,
            setProjection = { c, p -> c.withProjection(p) },
            restrictToId = { c, id -> c.withPurchaseId(id) },
            parseId = String::toLongOrNull,
            idOf = { it.id },
        )
        rpc.policy = PurchaseDataPolicy.POLICY
        rpc.doc = { op -> RepositoryApiDocs.rpcDoc("purchase", op) }
        rpc.register(routes)
    }
}
