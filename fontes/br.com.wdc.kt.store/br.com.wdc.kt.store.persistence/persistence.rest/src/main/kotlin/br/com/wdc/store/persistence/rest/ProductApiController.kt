package br.com.wdc.store.persistence.rest

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.exception.NotFoundException
import br.com.wdc.framework.domain.exception.TransactionConflictException
import br.com.wdc.framework.domain.exception.TransactionLimitExceededException
import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.RestCall
import br.com.wdc.framework.rest.RestResponse
import br.com.wdc.framework.rest.rpc.RepositoryRpc
import br.com.wdc.store.domain.StoreTransactions
import br.com.wdc.store.domain.product.Product
import br.com.wdc.store.domain.product.ProductCodec
import br.com.wdc.store.domain.product.ProductCriteria
import br.com.wdc.store.domain.product.ProductRepository
import br.com.wdc.store.persistence.rest.doc.RepositoryApiDocs
import br.com.wdc.store.persistence.rest.security.ProductDataPolicy
import br.com.wdc.framework.domain.projection.ProjectionValues
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * Product REST endpoints: the repository RPC, read and written with the [ProductCodec] — the same one the client
 * uses —, plus the image. Access is decided by [ProductDataPolicy]; writing the image is checked as an update of the
 * `image` field, and reading it is public.
 */
internal object ProductApiController {

    private val LOG = Log.getLogger("ProductApiController")
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
        rpc.policy = ProductDataPolicy.POLICY
        rpc.doc = { op -> RepositoryApiDocs.rpcDoc("product", op) }
        rpc.register(routes)

        routes.get("/api/repo/product/{id}/image", doc = RepositoryApiDocs.imageDoc("get")) { fetchImage(it) }
        routes.put("/api/repo/product/{id}/image", doc = RepositoryApiDocs.imageDoc("put")) {
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
        } catch (e: NotFoundException) {
            throw e // 404: the product doesn't exist (204 is "no image")
        } catch (e: Exception) {
            LOG.error("Fetching product image", e)
            return RestResponse.status(500, mapOf("error" to "Failed to fetch image"))
        }

        if (resultBytes == null) {
            return RestResponse.noContent()
        }
        // The URL is mutable (PUT replaces the image): the browser caches it, but revalidates by ETag on every use.
        val etag = etagOf(resultBytes)
        val headers = mapOf("Cache-Control" to "no-cache", "ETag" to etag)
        if (call.header("If-None-Match")?.split(',')?.any { it.trim() == etag } == true) {
            return RestResponse(304, null, headers)
        }
        return RestResponse(200, resultBytes, headers, contentType = "image/png")
    }

    private fun etagOf(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return "\"" + digest.take(16).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') } + "\""
    }

    private fun evictImage(id: Long) {
        imageCache.keys.filter { it == "$id" || it.startsWith("${id}_") }.forEach { imageCache.remove(it) }
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
            // an update of the image field, by the product policy
            ProductDataPolicy.POLICY.accessFor(call.request.principal).checkUpdate(
                Product().apply { this.id = id; image = imageBytes },
                Product().apply { image = ProjectionValues.bin },
            ) { _, projection -> repo().fetchById(id, projection) }
            val success = call.inTransaction {
                val updated = repo().updateImage(id, imageBytes)
                // Only after commit: clearing earlier would let a GET in between (which reads the committed, old
                // image) put it back in the cache. In a remote transaction, the commit is the client's.
                val transactions = StoreTransactions.BEAN.getOrNull()
                if (transactions == null) evictImage(id) else transactions.afterCommit { evictImage(id) }
                updated
            }
            return mapOf("success" to success)
        } catch (e: Exception) {
            // transaction and access refusals answer with their own status
            if (e is TransactionConflictException || e is TransactionLimitExceededException || e is AccessDeniedException || e is NotFoundException) throw e
            LOG.error("Updating product image", e)
            return RestResponse.status(500, mapOf("error" to "Failed to update image"))
        }
    }
}
