package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.product.ProductCodec
import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.domain.security.SecurityContext
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * Endpoints REST de produto. Lê e escreve com o [ProductCodec] — o mesmo que o cliente usa —, sem reflexão.
 * O controle de acesso é feito aqui ([ApiSecurity]): ler exige `product:read`, escrever `product:write`; a
 * leitura da imagem é pública.
 */
class ProductApiController {

    companion object {
        private val LOG = Log.getLogger("ProductApiController")
        private const val ENTITY = "product"
        private const val IMAGE_CACHE_MAX_SIZE = 200
        // Key: "id" for original, "id_size" for resized
        private val imageCache = ConcurrentHashMap<String, ByteArray>()

        fun configure(config: JavalinConfig) {
            val ctrl = ProductApiController()
            config.routes.post("/api/repo/product/insert", ctrl::insert)
            config.routes.post("/api/repo/product/update", ctrl::update)
            config.routes.post("/api/repo/product/delete", ctrl::delete)
            config.routes.post("/api/repo/product/count", ctrl::count)
            config.routes.post("/api/repo/product/fetch", ctrl::fetch)
            config.routes.post("/api/repo/product/fetch-page", ctrl::fetchPage)
            config.routes.post("/api/repo/product/fetch-by-id", ctrl::fetchByIdPost)
            config.routes.get("/api/repo/product/{id}", ctrl::fetchById)
            config.routes.get("/api/repo/product/{id}/image", ctrl::fetchImage)
            config.routes.put("/api/repo/product/{id}/image", ctrl::updateImage)
        }

        private fun repo(): ProductRepository = ProductRepository.BEAN.get()
    }

    private val codec = ProductCodec()

    /** Lê o pedido de consulta; sem projeção, vale a padrão do repositório (tudo menos a imagem). */
    private fun readFetchRequest(ctx: Context): FetchRequest<ProductCriteria> {
        val request = codec.readFetchRequest(ctx.jsonBody(), ProductCriteria()) { c, prj -> c.withProjection(prj) }
        if (request.criteria.projection == null) {
            request.criteria.withProjection(repo().newProjection())
        }
        return request
    }

    /** O catálogo é de todos: não há restrição por usuário. */
    private fun scoped(@Suppress("UNUSED_PARAMETER") sc: SecurityContext?, criteria: ProductCriteria): ProductCriteria = criteria

    private fun insert(ctx: Context) {
        ApiSecurity.require(ENTITY, "write")
        val product = codec.readEntity(ctx.jsonBody())
        val success = transactional(ctx) { repo().insert(product) }
        ctx.jsonResult { it.beginObject().name("success").value(success).name("id").value(product.id ?: -1L).endObject() }
    }

    /** As chaves presentes no corpo dizem o que atualizar — inclusive para `null`. */
    private fun update(ctx: Context) {
        ApiSecurity.require(ENTITY, "write")
        val data = codec.readEntityForUpdate(ctx.jsonBody())
        val success = transactional(ctx) { repo().update(data.entity, null, data.projection) }
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
        ctx.jsonResult { codec.writeItems(it, items) }
    }

    private fun fetchPage(ctx: Context) {
        val request = readFetchRequest(ctx)
        scoped(ApiSecurity.require(ENTITY, "read"), request.criteria)
        val page = blocking { repo().fetchPage(request.criteria, request.page, request.pageSize) }
        ctx.jsonResult { codec.writeItems(it, page.items, page.totalItems) }
    }

    private fun fetchById(ctx: Context) {
        val id = ctx.pathParam("id").toLongOrNull() ?: throw InvalidRequestException("id de produto inválido")
        respondEntity(ctx, fetchOne(id, null))
    }

    private fun fetchByIdPost(ctx: Context) {
        var id: Long? = null
        var projection: Product? = null
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
    private fun fetchOne(id: Long, projection: Product?): Product? {
        val criteria = scoped(ApiSecurity.require(ENTITY, "read"), ProductCriteria().withProductId(id).withProjection(projection ?: repo().newProjection()))
        return blocking { repo().fetch(criteria, 0, 1) }.firstOrNull()
    }

    private fun respondEntity(ctx: Context, product: Product?) {
        if (product == null) {
            ctx.status(404).json(mapOf("error" to "Not found"))
            return
        }
        ctx.jsonResult { codec.writeEntity(it, product) }
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
            val originalBytes = imageCache["$id"] ?: blocking { repo().fetchImage(id) }?.also { bytes ->
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

        ApiSecurity.require(ENTITY, "write")
        try {
            val imageBytes = ctx.bodyAsBytes()
            val success = transactional(ctx) { repo().updateImage(id, imageBytes) }
            imageCache.keys.filter { it == "$id" || it.startsWith("${id}_") }.forEach { imageCache.remove(it) }
            ctx.json(mapOf("success" to success))
        } catch (e: Exception) {
            LOG.error("Updating product image", e)
            ctx.status(500).json(mapOf("error" to "Failed to update image"))
        }
    }
}
