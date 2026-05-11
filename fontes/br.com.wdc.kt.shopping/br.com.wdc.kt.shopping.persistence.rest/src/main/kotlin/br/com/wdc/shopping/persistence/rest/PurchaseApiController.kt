package br.com.wdc.shopping.persistence.rest

import br.com.wdc.shopping.domain.criteria.PurchaseCriteria
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.utils.ProjectionValues
import com.google.gson.JsonObject
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import java.util.Collections

class PurchaseApiController {

    companion object {
        fun configure(config: JavalinConfig) {
            val ctrl = PurchaseApiController()
            config.routes.post("/api/repo/purchase/insert", ctrl::insert)
            config.routes.post("/api/repo/purchase/update", ctrl::update)
            config.routes.post("/api/repo/purchase/upsert", ctrl::upsert)
            config.routes.post("/api/repo/purchase/delete", ctrl::delete)
            config.routes.post("/api/repo/purchase/count", ctrl::count)
            config.routes.post("/api/repo/purchase/fetch", ctrl::fetch)
            config.routes.post("/api/repo/purchase/fetchById", ctrl::fetchByIdPost)
            config.routes.get("/api/repo/purchase/{id}", ctrl::fetchById)
        }

        private fun repo(): PurchaseRepository = PurchaseRepository.BEAN.get()

        private fun fullProjectionWithItems(): Purchase {
            val pv = ProjectionValues

            val product = Product().apply {
                id = pv.i64
                name = pv.str
                price = pv.f64
            }

            val item = PurchaseItem().apply {
                id = pv.i64
                amount = pv.i32
                price = pv.f64
                this.product = product
            }

            return Purchase().apply {
                id = pv.i64
                buyDate = pv.offsetDateTime
                user = User().apply {
                    id = pv.i64
                    name = pv.str
                }
                items = Collections.singletonList(item)
            }
        }

        private fun simpleProjection(): Purchase {
            val pv = ProjectionValues
            return Purchase().apply {
                id = pv.i64
                buyDate = pv.offsetDateTime
                user = User().apply {
                    id = pv.i64
                    name = pv.str
                }
            }
        }

        private fun clearCircularRefs(purchases: List<Purchase>) {
            for (purchase in purchases) {
                clearCircularRefs(purchase)
            }
        }

        private fun clearCircularRefs(purchase: Purchase) {
            purchase.items?.forEach { item ->
                item.purchase = null
            }
        }

        private fun parseCriteria(body: JsonObject): PurchaseCriteria {
            val criteria = PurchaseCriteria()
            if (hasValue(body, "purchaseId")) criteria.withPurchaseId(body.get("purchaseId").asLong)
            if (hasValue(body, "userId")) criteria.withUserId(body.get("userId").asLong)
            if (hasValue(body, "offset")) criteria.withOffset(body.get("offset").asInt)
            if (hasValue(body, "limit")) criteria.withLimit(body.get("limit").asInt)
            if (hasValue(body, "orderBy")) criteria.withOrderBy(PurchaseCriteria.OrderBy.valueOf(body.get("orderBy").asString))
            return criteria
        }

        private fun hasValue(obj: JsonObject, field: String): Boolean {
            return obj.has(field) && !obj.get(field).isJsonNull
        }

        private fun json(ctx: Context, obj: Any) {
            ctx.contentType("application/json")
            ctx.result(ApiGson.instance.toJson(obj))
        }
    }

    private fun insert(ctx: Context) {
        val purchase = ApiGson.instance.fromJson(ctx.body(), Purchase::class.java)
        val success = repo().insert(purchase)
        json(ctx, mapOf("success" to success, "id" to (purchase.id ?: -1L)))
    }

    private fun update(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), JsonObject::class.java)
        val newEntity = ApiGson.instance.fromJson(body.get("newEntity"), Purchase::class.java)
        val oldEntity = ApiGson.instance.fromJson(body.get("oldEntity"), Purchase::class.java)
        val success = repo().update(newEntity, oldEntity)
        json(ctx, mapOf("success" to success))
    }

    private fun upsert(ctx: Context) {
        val purchase = ApiGson.instance.fromJson(ctx.body(), Purchase::class.java)
        val success = repo().insertOrUpdate(purchase)
        json(ctx, mapOf("success" to success, "id" to (purchase.id ?: -1L)))
    }

    private fun delete(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), JsonObject::class.java)
        val count = repo().delete(parseCriteria(body))
        json(ctx, mapOf("count" to count))
    }

    private fun count(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), JsonObject::class.java)
        val count = repo().count(parseCriteria(body))
        json(ctx, mapOf("count" to count))
    }

    private fun fetch(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), JsonObject::class.java)
        val criteria = parseCriteria(body)

        val projection = ApiGson.parseProjection(body, Purchase::class.java)
        if (projection == null) {
            val includeItems = hasValue(body, "includeItems") && body.get("includeItems").asBoolean
            criteria.withProjection(if (includeItems) fullProjectionWithItems() else simpleProjection())
        } else {
            criteria.withProjection(projection)
        }

        val items = repo().fetch(criteria)
        clearCircularRefs(items)
        json(ctx, mapOf("items" to items))
    }

    private fun fetchById(ctx: Context) {
        val id = ctx.pathParam("id").toLong()
        val result = repo().fetchById(id, fullProjectionWithItems())
        if (result == null) {
            ctx.status(404).json(mapOf("error" to "Not found"))
            return
        }
        clearCircularRefs(result)
        json(ctx, result)
    }

    private fun fetchByIdPost(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), JsonObject::class.java)
        val id = body.get("id").asLong
        val projection = ApiGson.parseProjection(body, Purchase::class.java)
        val result = repo().fetchById(id, projection ?: fullProjectionWithItems())
        if (result == null) {
            ctx.status(404).json(mapOf("error" to "Not found"))
            return
        }
        clearCircularRefs(result)
        json(ctx, result)
    }
}
