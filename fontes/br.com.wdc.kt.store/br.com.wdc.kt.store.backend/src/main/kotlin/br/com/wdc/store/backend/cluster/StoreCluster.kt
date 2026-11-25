package br.com.wdc.store.backend.cluster

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.remote.RemoteApplicationRegistry
import br.com.wdc.store.persistence.rest.ProductImageCache
import br.com.wdc.store.persistence.security.SessionEvents
import io.micrometer.core.instrument.Metrics

/**
 * What the WDC Store's instances tell each other on the [mesh]:
 *
 * - **a session ended** (signed out, or renewed into a new one): the others drop it from memory ([sessionEvents]);
 * - **a product image changed**: the others drop it from their image cache;
 * - **a remote view session was made here**: its tab moved to this instance, and the one it was on lets go of its
 *   copy, if no socket holds it there.
 *
 * When another instance connects (it may have published something while it could not), this one drops every session
 * and every image it holds — they are read again from the database. Remote view sessions are left to expire.
 */
class StoreCluster(val mesh: ClusterMesh) {

    init {
        // the series exist from the start, at zero, not from the first message
        for (type in listOf(SESSION_ENDED, IMAGE_CHANGED, REMOTE_SESSION_MADE)) {
            Metrics.counter("store.cluster.messages.sent", "type", type)
            Metrics.counter("store.cluster.messages.received", "type", type)
        }
        Metrics.counter("store.cluster.connections")
    }

    companion object {
        const val SESSION_ENDED = "session-ended"
        const val IMAGE_CHANGED = "image-changed"
        const val REMOTE_SESSION_MADE = "remote-session-made"

        private val LOG = Log.getLogger("StoreCluster")
    }

    /** The sessions that end, told to and by the other instances. */
    val sessionEvents: SessionEvents = object : SessionEvents {
        override fun sessionEnded(sessionId: String) = mesh.publish(SESSION_ENDED, sessionId)

        override fun listen(onEnded: (sessionId: String) -> Unit, onMissed: () -> Unit) {
            mesh.on(SESSION_ENDED, onEnded)
            mesh.onMissed(onMissed)
        }
    }

    /** Passes on the product images that change here, and drops the ones that change elsewhere. Closing stops it. */
    fun shareImageChanges(): AutoCloseable {
        mesh.on(IMAGE_CHANGED) { id -> id.toLongOrNull()?.let(ProductImageCache::evict) }
        mesh.onMissed(ProductImageCache::clear)
        return ProductImageCache.onChanged { mesh.publish(IMAGE_CHANGED, it.toString()) }
    }

    /** Tells the remote view sessions made here, and lets go of the ones made elsewhere. Closing stops it. */
    fun shareRemoteSessions(registry: RemoteApplicationRegistry): AutoCloseable {
        mesh.on(REMOTE_SESSION_MADE) { id ->
            if (registry.releaseIdle(id)) LOG.debug("Remote session {} moved to another instance: released here", id)
        }
        return registry.onCreated { mesh.publish(REMOTE_SESSION_MADE, it) }
    }
}
