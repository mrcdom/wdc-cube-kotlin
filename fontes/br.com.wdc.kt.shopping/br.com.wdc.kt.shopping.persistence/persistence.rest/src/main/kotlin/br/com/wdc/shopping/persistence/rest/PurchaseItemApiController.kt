package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.rpc.RepositoryRpc
import br.com.wdc.shopping.domain.exception.AccessDeniedException
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItem
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCodec
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.shopping.domain.purchaseitem.PurchaseItemRepository
import br.com.wdc.shopping.persistence.rest.doc.RepositoryApiDocs

/**
 * Purchase item REST endpoints: the repository RPC, read and written with the [PurchaseItemCodec] — the same one
 * the client uses. Access is checked here ([ApiSecurity]): permission and, for callers who don't reach everyone's
 * data, restriction to the items of their own purchases — on reads and writes.
 *
 * **The password of the purchase's user never leaves through here**: it is stripped from the requested
 * projection and from every written entity, whether security is on or not.
 */
internal object PurchaseItemApiController {

    private const val ENTITY = "purchase-item"

    private fun repo(): PurchaseItemRepository = PurchaseItemRepository.BEAN.get()

    private fun dropPassword(item: PurchaseItem?) {
        item?.purchase?.user?.password = null
    }

    /** The check runs inside the write's transaction: it sees a purchase opened in it and not yet committed. */
    private suspend fun requireOwnPurchase(owner: Long, purchaseId: Long?) {
        if (purchaseId == null || PurchaseRepository.BEAN.get().count(PurchaseCriteria().withPurchaseId(purchaseId).withUserId(owner)) == 0) {
            throw AccessDeniedException("Cannot write items of other user's purchase")
        }
    }

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
        rpc.rule = { op -> ApiSecurity.permission(ENTITY, op) }
        rpc.doc = { op -> RepositoryApiDocs.rpcDoc("purchase-item", op) }
        // callers who don't reach everyone's data only reach the items of their own purchases, whatever they ask for
        rpc.scope = { criteria, request -> ApiSecurity.ownerScope(request)?.let { criteria.userId.and().eq(it) } }
        rpc.sanitizeProjection = ::dropPassword
        rpc.sanitizeOutput = ::dropPassword
        rpc.beforeInsert = { item, request ->
            ApiSecurity.ownerScope(request)?.let { owner -> requireOwnPurchase(owner, item.purchaseId) }
        }
        // the scope already keeps the update to the caller's own items; an item can only move to another purchase of theirs
        rpc.beforeUpdate = { data, request ->
            val owner = ApiSecurity.ownerScope(request)
            if (owner != null) data.entity.purchaseId?.let { requireOwnPurchase(owner, it) }
        }
        rpc.register(routes)
    }
}
