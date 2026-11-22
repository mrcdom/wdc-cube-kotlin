package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.rpc.RepositoryRpc
import br.com.wdc.shopping.domain.exception.AccessDeniedException
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseCodec
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.persistence.rest.doc.RepositoryApiDocs

/**
 * Purchase REST endpoints: the repository RPC, read and written with the [PurchaseCodec] — the same one the
 * client uses. Access is checked here ([ApiSecurity]): permission and, for callers who don't reach everyone's
 * data, restriction to their own purchases — on reads and writes.
 *
 * **The password of the purchase's user never leaves through here**: it is stripped from the requested
 * projection and from every written entity, whether security is on or not.
 */
internal object PurchaseApiController {

    private const val ENTITY = "purchase"

    private fun repo(): PurchaseRepository = PurchaseRepository.BEAN.get()

    private fun dropPassword(purchase: Purchase?) {
        purchase?.user?.password = null
    }

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
        rpc.rule = { op -> ApiSecurity.permission(ENTITY, op) }
        rpc.doc = { op -> RepositoryApiDocs.rpcDoc("purchase", op) }
        // callers who don't reach everyone's data only reach their own purchases, whatever they ask for
        rpc.scope = { criteria, request -> ApiSecurity.ownerScope(request)?.let { criteria.userId.and().eq(it) } }
        rpc.sanitizeProjection = ::dropPassword
        rpc.sanitizeOutput = ::dropPassword
        rpc.beforeInsert = { purchase, request ->
            // a purchase always belongs to whoever makes it
            ApiSecurity.ownerScope(request)?.let { owner -> purchase.user = User().apply { id = owner } }
        }
        // the scope already keeps the update to the caller's own purchases; this keeps the purchase theirs
        rpc.beforeUpdate = { data, request ->
            val owner = ApiSecurity.ownerScope(request)
            if (owner != null && data.entity.user != null && data.entity.userId != owner) {
                throw AccessDeniedException("Cannot reassign a purchase to another user")
            }
        }
        rpc.register(routes)
    }
}
