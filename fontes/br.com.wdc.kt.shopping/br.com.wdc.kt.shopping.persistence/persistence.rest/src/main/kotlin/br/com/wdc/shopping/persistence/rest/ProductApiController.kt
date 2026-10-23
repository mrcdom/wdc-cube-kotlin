package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.exception.TransactionConflictException
import br.com.wdc.framework.domain.exception.TransactionLimitExceededException
import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.RestCall
import br.com.wdc.framework.rest.RestResponse
import br.com.wdc.framework.rest.rpc.RepositoryRpc
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.product.ProductCodec
import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.persistence.rest.doc.RepositoryApiDocs
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * Endpoints REST de produto: o RPC do repositório, lido e escrito com o [ProductCodec] — o mesmo que o cliente
 * usa —, mais a imagem. Ler exige `product:read`, escrever `product:write`; a leitura da imagem é pública.
 */
internal object ProductApiController {

    private val LOG = Log.getLogger("ProductApiController")
    private const val ENTITY = "product"
    private const val IMAGE_CACHE_MAX_SIZE = 200

    // Key: "id" for original, "id_size" for resized
    private val imageCache = ConcurrentHashMap<String, ByteArray>()

    private fun repo(): ProductRepository = ProductRepository.BEAN.get()

    fun register(routes: RestApi.Routes) {
        val rpc = RepositoryRpc<Product, ProductCriteria, Long>(
            basePath = "/api/repo/product",
            codec = ProductCodec(),
            repository = ::repo,
            newCriteria = ::ProductCriteria,
            setProjection = { c, p -> c.withProjection(p) },
            restrictToId = { c, id -> c.withProductId(id) },
            parseId = String::toLongOrNull,
            idOf = { it.id },
        )
        // o catálogo é de todos: não há restrição por usuário
        rpc.rule = { op -> ApiSecurity.permission(ENTITY, op) }
        rpc.doc = { op -> RepositoryApiDocs.rpcDoc("product", op) }
        rpc.register(routes)

        routes.get("/api/repo/product/{id}/image", doc = RepositoryApiDocs.imageDoc("get")) { fetchImage(it) }
        routes.put("/api/repo/product/{id}/image", rule = ApiSecurity.permission(ENTITY, "write"), doc = RepositoryApiDocs.imageDoc("put")) {
            updateImage(it)
        }
    }

    private fun imageId(call: RestCall): Long = call.path("id").toLongOrNull() ?: throw InvalidRequestException("Invalid product ID")

    private suspend fun fetchImage(call: RestCall): Any? {
        val id = imageId(call)
        val size = call.query("size")?.toIntOrNull()?.coerceIn(16, 1024)
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
            return RestResponse.status(500, mapOf("error" to "Failed to fetch image"))
        }

        if (resultBytes == null) {
            return RestResponse.noContent()
        }
        return RestResponse(200, resultBytes, mapOf("Cache-Control" to "public, max-age=86400, immutable"), contentType = "image/png")
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

    private suspend fun updateImage(call: RestCall): Any? {
        val id = imageId(call)
        try {
            val imageBytes = call.bodyBytes()
            val success = call.inTransaction { repo().updateImage(id, imageBytes) }
            imageCache.keys.filter { it == "$id" || it.startsWith("${id}_") }.forEach { imageCache.remove(it) }
            return mapOf("success" to success)
        } catch (e: Exception) {
            // recusas da transação e do acesso respondem com o status delas
            if (e is TransactionConflictException || e is TransactionLimitExceededException || e is AccessDeniedException) throw e
            LOG.error("Updating product image", e)
            return RestResponse.status(500, mapOf("error" to "Failed to update image"))
        }
    }
}
