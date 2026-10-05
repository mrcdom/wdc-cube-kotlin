package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.shopping.domain.purchase.Purchase
import br.com.wdc.shopping.domain.purchase.PurchaseCodec
import br.com.wdc.shopping.domain.purchase.PurchaseCriteria
import br.com.wdc.shopping.domain.purchase.PurchaseRepository
import io.javalin.config.JavalinConfig
import io.javalin.http.Context

/**
 * Endpoints REST de compra. Lê e escreve com o [PurchaseCodec] — o mesmo que o cliente usa —, sem reflexão.
 * O controle de acesso é do repositório registrado (decorado quando a segurança está ligada).
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

    private fun insert(ctx: Context) {
        val purchase = codec.readEntity(ctx.jsonBody())
        val success = blocking { repo().insert(purchase) }
        ctx.jsonResult { it.beginObject().name("success").value(success).name("id").value(purchase.id ?: -1L).endObject() }
    }

    /** As chaves presentes no corpo dizem o que atualizar — inclusive para `null`. */
    private fun update(ctx: Context) {
        val data = codec.readEntityForUpdate(ctx.jsonBody())
        val success = blocking { repo().update(data.entity, null, data.projection) }
        ctx.jsonField("success", success)
    }

    private fun delete(ctx: Context) {
        val criteria = readFetchRequest(ctx).criteria
        ctx.jsonField("count", blocking { repo().delete(criteria) })
    }

    private fun count(ctx: Context) {
        val criteria = readFetchRequest(ctx).criteria
        ctx.jsonField("count", blocking { repo().count(criteria) })
    }

    private fun fetch(ctx: Context) {
        val request = readFetchRequest(ctx)
        val items = blocking { repo().fetch(request.criteria, request.offset, request.limit) }
        items.forEach(::dropPassword)
        ctx.jsonResult { codec.writeItems(it, items) }
    }

    private fun fetchPage(ctx: Context) {
        val request = readFetchRequest(ctx)
        val page = blocking { repo().fetchPage(request.criteria, request.page, request.pageSize) }
        page.items.forEach(::dropPassword)
        ctx.jsonResult { codec.writeItems(it, page.items, page.totalItems) }
    }

    private fun fetchById(ctx: Context) {
        val id = ctx.pathParam("id").toLongOrNull() ?: throw InvalidRequestException("id de compra inválido")
        respondEntity(ctx, blocking { repo().fetchById(id) })
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
        val entityId = id ?: throw InvalidRequestException("fetch-by-id exige o id")
        dropPassword(projection)
        respondEntity(ctx, blocking { repo().fetchById(entityId, projection) })
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
