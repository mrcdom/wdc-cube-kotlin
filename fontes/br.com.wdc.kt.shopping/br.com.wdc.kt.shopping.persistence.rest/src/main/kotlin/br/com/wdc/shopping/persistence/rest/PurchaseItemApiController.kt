package br.com.wdc.shopping.persistence.rest

import br.com.wdc.shopping.domain.criteria.PurchaseItemCriteria
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.utils.ProjectionValues
import com.google.gson.JsonObject
import io.javalin.config.JavalinConfig
import io.javalin.http.Context

class PurchaseItemApiController {

    companion object {
        fun configure(config: JavalinConfig) {
            val ctrl = PurchaseItemApiController()
            config.routes.post("/api/repo/purchase-item/insert", ctrl::insert)
            config.routes.post("/api/repo/purchase-item/update", ctrl::update)
            config.routes.post("/api/repo/purchase-item/upsert", ctrl::upsert)
            config.routes.post("/api/repo/purchase-item/delete", ctrl::delete)
            config.routes.post("/api/repo/purchase-item/count", ctrl::count)
            config.routes.post("/api/repo/purchase-item/fetch", ctrl::fetch)
            config.routes.post("/api/repo/purchase-item/fetchById", ctrl::fetchByIdPost)
            config.routes.get("/api/repo/purchase-item/{id}", ctrl::fetchById)
        }

        private fun repo(): PurchaseItemRepository = PurchaseItemRepository.BEAN.get()

        private fun fullProjection(): PurchaseItem {
            val pv = ProjectionValues

            val product = Product().apply {
                id = pv.i64
                name = pv.str
                price = pv.f64
            }

            return PurchaseItem().apply {
                id = pv.i64
                amount = pv.i32
                price = pv.f64
                this.product = product
            }
        }

        private fun parseCriteria(body: JsonObject): PurchaseItemCriteria {
            val criteria = PurchaseItemCriteria()
            if (hasValue(body, "purchaseItemId")) criteria.withPurchaseItemId(body.get("purchaseItemId").asLong)
            if (hasValue(body, "purchaseId")) criteria.withPurchaseId(body.get("purchaseId").asLong)
            if (hasValue(body, "productId")) criteria.withProductId(body.get("productId").asLong)
            if (hasValue(body, "userId")) criteria.withUserId(body.get("userId").asLong)
            if (hasValue(body, "offset")) criteria.withOffset(body.get("offset").asInt)
            if (hasValue(body, "limit")) {
                val limit = body.get("limit").asInt
                if (limit >= 0) criteria.withLimit(limit)
            }
            if (hasValue(body, "orderBy")) criteria.withOrderBy(PurchaseItemCriteria.OrderBy.valueOf(body.get("orderBy").asString))
            return criteria
        }

        private fun hasValue(obj: JsonObject, field: String): Boolean {
            return obj.has(field) && !obj.get(field).isJsonNull
        }

        private fun json(ctx: Context, obj: Any) {
            ctx.contentType("application/json")
            ctx.result(ApiGson.instance.toJson(obj))
        }

        private fun setPurchaseFromJson(body: JsonObject, item: PurchaseItem) {
            if (hasValue(body, "purchaseId")) {
                item.purchase = Purchase().apply { id = body.get("purchaseId").asLong }
            } else if (hasValue(body, "purchase")) {
                val purchaseNode = body.getAsJsonObject("purchase")
                if (purchaseNode != null && hasValue(purchaseNode, "id")) {
                    item.purchase = Purchase().apply { id = purchaseNode.get("id").asLong }
                }
            }
        }
    }

    private fun insert(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), JsonObject::class.java)
        val item = ApiGson.instance.fromJson(body, PurchaseItem::class.java)
        setPurchaseFromJson(body, item)
        val success = repo().insert(item)
        json(ctx, mapOf("success" to success, "id" to (item.id ?: -1L)))
    }

    private fun update(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), JsonObject::class.java)
        val newEntity = ApiGson.instance.fromJson(body.get("newEntity"), PurchaseItem::class.java)
        val oldEntity = ApiGson.instance.fromJson(body.get("oldEntity"), PurchaseItem::class.java)
        val success = repo().update(newEntity, oldEntity)
        json(ctx, mapOf("success" to success))
    }

    private fun upsert(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), JsonObject::class.java)
        val item = ApiGson.instance.fromJson(body, PurchaseItem::class.java)
        setPurchaseFromJson(body, item)
        val success = repo().insertOrUpdate(item)
        json(ctx, mapOf("success" to success, "id" to (item.id ?: -1L)))
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
        val projection = ApiGson.parseProjection(body, PurchaseItem::class.java)
        criteria.withProjection(projection ?: fullProjection())
        val items = repo().fetch(criteria)
        items.forEach { it.purchase = null }
        json(ctx, mapOf("items" to items))
    }

    private fun fetchById(ctx: Context) {
        val id = ctx.pathParam("id").toLong()
        val result = repo().fetchById(id, fullProjection())
        if (result == null) {
            ctx.status(404).json(mapOf("error" to "Not found"))
            return
        }
        result.purchase = null
        json(ctx, result)
    }

    private fun fetchByIdPost(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), JsonObject::class.java)
        val id = body.get("id").asLong
        val projection = ApiGson.parseProjection(body, PurchaseItem::class.java)
        val result = repo().fetchById(id, projection ?: fullProjection())
        if (result == null) {
            ctx.status(404).json(mapOf("error" to "Not found"))
            return
        }
        result.purchase = null
        json(ctx, result)
    }
}
