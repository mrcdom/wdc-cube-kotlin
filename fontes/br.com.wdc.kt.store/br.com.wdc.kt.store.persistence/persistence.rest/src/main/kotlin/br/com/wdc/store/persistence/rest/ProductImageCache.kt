package br.com.wdc.store.persistence.rest

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The product images this instance already served, original and resized, so that each is read from the database and
 * resized once. An image saved here is dropped by [changed], which also tells the [listeners][onChanged] — with several
 * backend instances, the ones that pass it on to the others, which drop it with [evict].
 */
object ProductImageCache {

    private const val MAX_SIZE = 200

    // Key: "id" for the original, "id_size" for a resized one
    private val images = ConcurrentHashMap<String, ByteArray>()

    private val listeners = CopyOnWriteArrayList<(productId: Long) -> Unit>()

    internal operator fun get(key: String): ByteArray? = images[key]

    internal fun put(key: String, bytes: ByteArray) {
        if (images.size < MAX_SIZE) images[key] = bytes
    }

    /** The image of [productId] changed here: drops it and tells the listeners. */
    internal fun changed(productId: Long) {
        evict(productId)
        for (listener in listeners) listener(productId)
    }

    /** Drops the image of [productId], original and resized, without telling anyone. */
    fun evict(productId: Long) {
        images.keys.removeIf { it == "$productId" || it.startsWith("${productId}_") }
    }

    /** Drops every image. */
    fun clear() {
        images.clear()
    }

    /** Calls [listener] with the product whose image changed on this instance. Closing the result stops it. */
    fun onChanged(listener: (productId: Long) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }
}
