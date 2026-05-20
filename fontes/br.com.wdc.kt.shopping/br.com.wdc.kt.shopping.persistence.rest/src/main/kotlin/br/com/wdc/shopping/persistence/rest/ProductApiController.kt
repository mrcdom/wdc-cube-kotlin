package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.shopping.domain.criteria.ProductCriteria
import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.repositories.Page
import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.utils.ProjectionValues
import com.google.gson.JsonObject
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

class ProductApiController {

    companion object {
        private val LOG = Log.getLogger("ProductApiController")
        private const val IMAGE_CACHE_MAX_SIZE = 200
        // Key: "id" for original, "id_size" for resized
        private val imageCache = ConcurrentHashMap<String, ByteArray>()

        fun configure(config: JavalinConfig) {
            val ctrl = ProductApiController()
            config.routes.post("/api/repo/product/insert", ctrl::insert)
            config.routes.post("/api/repo/product/update", ctrl::update)
            config.routes.post("/api/repo/product/upsert", ctrl::upsert)
            config.routes.post("/api/repo/product/delete", ctrl::delete)
            config.routes.post("/api/repo/product/count", ctrl::count)
            config.routes.post("/api/repo/product/fetch", ctrl::fetch)
            config.routes.post("/api/repo/product/fetchPage", ctrl::fetchPage)
            config.routes.post("/api/repo/product/fetchById", ctrl::fetchByIdPost)
            config.routes.get("/api/repo/product/{id}", ctrl::fetchById)
            config.routes.get("/api/repo/product/{id}/image", ctrl::fetchImage)
            config.routes.put("/api/repo/product/{id}/image", ctrl::updateImage)
        }

        private fun repo(): ProductRepository = ProductRepository.BEAN.get()

        private fun fullProjection(): Product {
            val pv = ProjectionValues
            return Product().apply {
                id = pv.i64
                name = pv.str
                price = pv.f64
                description = pv.str
            }
        }

        private fun json(ctx: Context, obj: Any) {
            ctx.contentType("application/json")
            ctx.result(ApiGson.instance.toJson(obj))
        }

        private fun parseCriteria(body: JsonObject): ProductCriteria {
            val criteria = ProductCriteria()
            if (hasValue(body, "productId")) criteria.withProductId(body.get("productId").asLong)
            if (hasValue(body, "offset")) criteria.withOffset(body.get("offset").asInt)
            if (hasValue(body, "limit")) {
                val limit = body.get("limit").asInt
                if (limit >= 0) criteria.withLimit(limit)
            }
            if (hasValue(body, "orderBy")) criteria.withOrderBy(ProductCriteria.OrderBy.valueOf(body.get("orderBy").asString))
            return criteria
        }

        private fun hasValue(obj: JsonObject, field: String): Boolean {
            return obj.has(field) && !obj.get(field).isJsonNull
        }
    }

    private fun insert(ctx: Context) {
        val product = ApiGson.instance.fromJson(ctx.body(), Product::class.java)
        val success = repo().insert(product)
        json(ctx, mapOf("success" to success, "id" to (product.id ?: -1L)))
    }

    private fun update(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), JsonObject::class.java)
        val newEntity = ApiGson.instance.fromJson(body.get("newEntity"), Product::class.java)
        val oldEntity = ApiGson.instance.fromJson(body.get("oldEntity"), Product::class.java)
        val success = repo().update(newEntity, oldEntity)
        json(ctx, mapOf("success" to success))
    }

    private fun upsert(ctx: Context) {
        val product = ApiGson.instance.fromJson(ctx.body(), Product::class.java)
        val success = repo().insertOrUpdate(product)
        json(ctx, mapOf("success" to success, "id" to (product.id ?: -1L)))
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
        val projection = ApiGson.parseProjection(body, Product::class.java)
        criteria.withProjection(projection ?: fullProjection())
        val items = repo().fetch(criteria)
        json(ctx, mapOf("items" to items))
    }

    private fun fetchPage(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), JsonObject::class.java)
        val criteria = parseCriteria(body)
        val projection = ApiGson.parseProjection(body, Product::class.java)
        criteria.withProjection(projection ?: fullProjection())
        val page = repo().fetchPage(criteria)
        json(ctx, mapOf("items" to page.items, "totalCount" to page.totalCount))
    }

    private fun fetchById(ctx: Context) {
        val id = ctx.pathParam("id").toLong()
        val result = repo().fetchById(id, fullProjection())
        if (result == null) {
            ctx.status(404).json(mapOf("error" to "Not found"))
            return
        }
        json(ctx, result)
    }

    private fun fetchByIdPost(ctx: Context) {
        val body = ApiGson.instance.fromJson(ctx.body(), JsonObject::class.java)
        val id = body.get("id").asLong
        val projection = ApiGson.parseProjection(body, Product::class.java)
        val result = repo().fetchById(id, projection ?: fullProjection())
        if (result == null) {
            ctx.status(404).json(mapOf("error" to "Not found"))
            return
        }
        json(ctx, result)
    }

    private fun fetchImage(ctx: Context) {
        val id: Long
        try {
            id = ctx.pathParam("id").toLong()
        } catch (e: NumberFormatException) {
            LOG.debug(e.message ?: "Invalid product ID")
            ctx.status(400).json(mapOf("error" to "Invalid product ID"))
            return
        }

        val size = ctx.queryParam("size")?.toIntOrNull()?.coerceIn(16, 1024)
        val cacheKey = if (size != null) "${id}_$size" else "$id"

        val resultBytes: ByteArray? = imageCache[cacheKey] ?: try {
            val originalBytes = imageCache["$id"] ?: repo().fetchImage(id)?.also { bytes ->
                if (imageCache.size < IMAGE_CACHE_MAX_SIZE) {
                    imageCache["$id"] = bytes
                }
            }
            if (originalBytes != null && size != null) {
                val resized = resizeImage(originalBytes, size)
                if (imageCache.size < IMAGE_CACHE_MAX_SIZE) {
                    imageCache[cacheKey] = resized
                }
                resized
            } else {
                originalBytes
            }
        } catch (e: Exception) {
            LOG.error("Fetching product image", e)
            ctx.status(500).json(mapOf("error" to "Failed to fetch image"))
            return
        }

        if (resultBytes == null) {
            ctx.status(204)
            return
        }
        ctx.header("Cache-Control", "public, max-age=86400, immutable")
        ctx.contentType("image/png")
        ctx.result(resultBytes)
    }

    private fun resizeImage(originalBytes: ByteArray, targetSize: Int): ByteArray {
        val original = ImageIO.read(ByteArrayInputStream(originalBytes))
            ?: return originalBytes
        val w = original.width
        val h = original.height
        val scale = targetSize.toDouble() / maxOf(w, h)
        if (scale >= 1.0) return originalBytes
        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)
        val resized = BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB)
        val g = resized.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(original, 0, 0, newW, newH, null)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(resized, "PNG", out)
        return out.toByteArray()
    }

    private fun updateImage(ctx: Context) {
        val id: Long
        try {
            id = ctx.pathParam("id").toLong()
        } catch (e: NumberFormatException) {
            LOG.debug(e.message ?: "Invalid product ID")
            ctx.status(400).json(mapOf("error" to "Invalid product ID"))
            return
        }

        try {
            val imageBytes = ctx.bodyAsBytes()
            val success = repo().updateImage(id, imageBytes)
            imageCache.keys.filter { it == "$id" || it.startsWith("${id}_") }.forEach { imageCache.remove(it) }
            ctx.json(mapOf("success" to success))
        } catch (e: Exception) {
            LOG.error("Updating product image", e)
            ctx.status(500).json(mapOf("error" to "Failed to update image"))
        }
    }
}
