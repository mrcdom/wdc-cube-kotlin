package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseCodec
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import br.com.wdc.shopping.domain.exception.AccessDeniedException
import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.domain.user.User
import io.javalin.config.JavalinConfig
import io.javalin.http.Context

/**
 * Endpoints REST de compra. Lê e escreve com o [PurchaseCodec] — o mesmo que o cliente usa —, sem reflexão.
 * O controle de acesso é feito aqui ([ApiSecurity]): permissão e, para quem não alcança os dados de todos,
 * restrição às próprias compras — na leitura e na escrita.
 *
 * **A senha do usuário da compra nunca sai por aqui**: é retirada da projeção pedida e de toda entidade
 * escrita, esteja a segurança ligada ou não.
 */
class PurchaseApiController {

    companion object {
        fun configure(config: JavalinConfig) {
            val ctrl = PurchaseApiController()
            config.routes.post("/api/repo/purchase/insert", ctrl::insert)
            config.routes.post("/api/repo/purchase/update", ctrl::update)
            config.routes.post("/api/repo/purchase/delete", ctrl::delete)
            config.routes.post("/api/repo/purchase/count", ctrl::count)
            config.routes.post("/api/repo/purchase/fetch", ctrl::fetch)
            config.routes.post("/api/repo/purchase/fetch-page", ctrl::fetchPage)
            config.routes.post("/api/repo/purchase/fetch-by-id", ctrl::fetchByIdPost)
            config.routes.get("/api/repo/purchase/{id}", ctrl::fetchById)
        }

        private const val ENTITY = "purchase"

        private fun repo(): PurchaseRepository = PurchaseRepository.BEAN.get()

        private fun dropPassword(purchase: Purchase?) {
            purchase?.user?.password = null
        }
    }

    private val codec = PurchaseCodec()

    /** Lê o pedido de consulta; sem projeção, vale a padrão do repositório. */
    private fun readFetchRequest(ctx: Context): FetchRequest<PurchaseCriteria> {
        val request = codec.readFetchRequest(ctx.jsonBody(), PurchaseCriteria()) { c, prj -> c.withProjection(prj) }
        if (request.criteria.projection == null) {
            request.criteria.withProjection(repo().newProjection())
        }
        dropPassword(request.criteria.projection)
        return request
    }

    /** Quem não alcança os dados de todos só alcança as próprias compras, peça o que pedir. */
    private fun scoped(sc: SecurityContext?, criteria: PurchaseCriteria): PurchaseCriteria {
        ApiSecurity.ownerScope(sc)?.let { criteria.userId.eq(it) }
        return criteria
    }

    private fun insert(ctx: Context) {
        val owner = ApiSecurity.ownerScope(ApiSecurity.require(ENTITY, "write"))
        val purchase = codec.readEntity(ctx.jsonBody())
        if (owner != null) {
            // a compra é sempre de quem a faz
            purchase.user = User().apply { id = owner }
        }
        val success = transactional(ctx) { repo().insert(purchase) }
        ctx.jsonResult { it.beginObject().name("success").value(success).name("id").value(purchase.id ?: -1L).endObject() }
    }

    /** As chaves presentes no corpo dizem o que atualizar — inclusive para `null`. */
    private fun update(ctx: Context) {
        val owner = ApiSecurity.ownerScope(ApiSecurity.require(ENTITY, "write"))
        val data = codec.readEntityForUpdate(ctx.jsonBody())
        val success = transactional(ctx) {
            if (owner != null) {
                val id = data.entity.id
                if (id == null || repo().count(PurchaseCriteria().withPurchaseId(id).withUserId(owner)) == 0) {
                    throw AccessDeniedException("Cannot modify other user's purchase")
                }
                if (data.entity.user != null && data.entity.userId != owner) {
                    throw AccessDeniedException("Cannot reassign a purchase to another user")
                }
            }
            repo().update(data.entity, null, data.projection)
        }
        ctx.jsonField("success", success)
    }

    private fun delete(ctx: Context) {
        val criteria = scoped(ApiSecurity.require(ENTITY, "delete"), readFetchRequest(ctx).criteria)
        ctx.jsonField("count", transactional(ctx) { repo().delete(criteria) })
    }

    private fun count(ctx: Context) {
        val criteria = scoped(ApiSecurity.require(ENTITY, "read"), readFetchRequest(ctx).criteria)
        ctx.jsonField("count", blocking { repo().count(criteria) })
    }

    private fun fetch(ctx: Context) {
        val request = readFetchRequest(ctx)
        scoped(ApiSecurity.require(ENTITY, "read"), request.criteria)
        val items = blocking { repo().fetch(request.criteria, request.offset, request.limit) }
        items.forEach(::dropPassword)
        ctx.jsonResult { codec.writeItems(it, items) }
    }

    private fun fetchPage(ctx: Context) {
        val request = readFetchRequest(ctx)
        scoped(ApiSecurity.require(ENTITY, "read"), request.criteria)
        val page = blocking { repo().fetchPage(request.criteria, request.page, request.pageSize) }
        page.items.forEach(::dropPassword)
        ctx.jsonResult { codec.writeItems(it, page.items, page.totalItems) }
    }

    private fun fetchById(ctx: Context) {
        val id = ctx.pathParam("id").toLongOrNull() ?: throw InvalidRequestException("id de compra inválido")
        respondEntity(ctx, fetchOne(id, null))
    }

    private fun fetchByIdPost(ctx: Context) {
        var id: Long? = null
        var projection: Purchase? = null
        val input = ctx.jsonBody()
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "id" -> id = InputCoerceUtils.asLong(input)
                "projection" -> projection = codec.readEntity(input)
                else -> input.skipValue()
            }
        }
        input.endObject()
        respondEntity(ctx, fetchOne(id ?: throw InvalidRequestException("fetch-by-id exige o id"), projection))
    }

    /** Buscar pela chave é a mesma consulta das outras: passa pela mesma permissão e pelo mesmo alcance. */
    private fun fetchOne(id: Long, projection: Purchase?): Purchase? {
        val criteria = scoped(ApiSecurity.require(ENTITY, "read"), PurchaseCriteria().withPurchaseId(id).withProjection(projection ?: repo().newProjection()))
        dropPassword(criteria.projection)
        return blocking { repo().fetch(criteria, 0, 1) }.firstOrNull()
    }

    private fun respondEntity(ctx: Context, purchase: Purchase?) {
        if (purchase == null) {
            ctx.status(404).json(mapOf("error" to "Not found"))
            return
        }
        dropPassword(purchase)
        ctx.jsonResult { codec.writeEntity(it, purchase) }
    }
}
