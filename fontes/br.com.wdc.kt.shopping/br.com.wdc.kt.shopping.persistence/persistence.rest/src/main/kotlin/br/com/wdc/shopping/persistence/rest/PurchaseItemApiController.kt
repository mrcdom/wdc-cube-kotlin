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
 * Endpoints REST de item de compra: o RPC do repositório, lido e escrito com o [PurchaseItemCodec] — o mesmo que
 * o cliente usa. O acesso é conferido aqui ([ApiSecurity]): permissão e, para quem não alcança os dados de todos,
 * restrição aos itens das próprias compras — na leitura e na escrita.
 *
 * **A senha do usuário da compra nunca sai por aqui**: é retirada da projeção pedida e de toda entidade
 * escrita, esteja a segurança ligada ou não.
 */
internal object PurchaseItemApiController {

    private const val ENTITY = "purchase-item"

    private fun repo(): PurchaseItemRepository = PurchaseItemRepository.BEAN.get()

    private fun dropPassword(item: PurchaseItem?) {
        item?.purchase?.user?.password = null
    }

    /** A conferência roda dentro da transação da escrita: enxerga a compra aberta nela e ainda não confirmada. */
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
        // quem não alcança os dados de todos só alcança os itens das próprias compras, peça o que pedir
        rpc.scope = { criteria, request -> ApiSecurity.ownerScope(request)?.let { criteria.userId.eq(it) } }
        rpc.sanitizeProjection = ::dropPassword
        rpc.sanitizeOutput = ::dropPassword
        rpc.beforeInsert = { item, request ->
            ApiSecurity.ownerScope(request)?.let { owner -> requireOwnPurchase(owner, item.purchaseId) }
        }
        rpc.beforeUpdate = { data, request ->
            val owner = ApiSecurity.ownerScope(request)
            if (owner != null) {
                val id = data.entity.id
                if (id == null || repo().count(PurchaseItemCriteria().withPurchaseItemId(id).withUserId(owner)) == 0) {
                    throw AccessDeniedException("Cannot modify other user's purchase item")
                }
                // o item só pode ser movido para outra compra do próprio usuário
                data.entity.purchaseId?.let { requireOwnPurchase(owner, it) }
            }
        }
        rpc.register(routes)
    }
}
