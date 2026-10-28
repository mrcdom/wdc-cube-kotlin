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
 * Endpoints REST de compra: o RPC do repositório, lido e escrito com o [PurchaseCodec] — o mesmo que o cliente
 * usa. O acesso é conferido aqui ([ApiSecurity]): permissão e, para quem não alcança os dados de todos,
 * restrição às próprias compras — na leitura e na escrita.
 *
 * **A senha do usuário da compra nunca sai por aqui**: é retirada da projeção pedida e de toda entidade
 * escrita, esteja a segurança ligada ou não.
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
        // quem não alcança os dados de todos só alcança as próprias compras, peça o que pedir
        rpc.scope = { criteria, request -> ApiSecurity.ownerScope(request)?.let { criteria.userId.eq(it) } }
        rpc.sanitizeProjection = ::dropPassword
        rpc.sanitizeOutput = ::dropPassword
        rpc.beforeInsert = { purchase, request ->
            // a compra é sempre de quem a faz
            ApiSecurity.ownerScope(request)?.let { owner -> purchase.user = User().apply { id = owner } }
        }
        rpc.beforeUpdate = { data, request ->
            val owner = ApiSecurity.ownerScope(request)
            if (owner != null) {
                val id = data.entity.id
                if (id == null || repo().count(PurchaseCriteria().withPurchaseId(id).withUserId(owner)) == 0) {
                    throw AccessDeniedException("Cannot modify other user's purchase")
                }
                if (data.entity.user != null && data.entity.userId != owner) {
                    throw AccessDeniedException("Cannot reassign a purchase to another user")
                }
            }
        }
        rpc.register(routes)
    }
}
