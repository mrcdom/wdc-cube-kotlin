package br.com.wdc.store.backend.cluster

import br.com.wdc.framework.commons.log.Log
import com.google.gson.Gson
import io.javalin.config.JavalinConfig
import io.javalin.websocket.WsConnectContext
import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The backend instances' own mesh: each instance keeps a WebSocket open to every other one ([start], with their
 * addresses) and sends on it what the others must know ([publish]: a type and an id); each receives, on the
 * connections the others open to it ([configure] adds the route, [PATH]), what they send ([on]).
 *
 * **Who may connect.** Both ends prove they hold [key] (derived from the configuration's secret, [ClusterKey]): the
 * receiving end sends a random challenge, the connecting end answers with an HMAC-SHA256 of it under the key and its
 * own challenge, the receiving end answers that one. Nothing is read, nor sent, before that. The messages themselves
 * travel as they are: between instances on another network than a private one, use `wss://`.
 *
 * **What may be missed.** A message published while the connection to a peer is down waits for it (the last 1000),
 * and the peer, when the connection comes back, also takes it as having missed something ([onMissed]) and drops
 * what it holds: a message lost on a connection that broke before anyone noticed costs nothing.
 * An address that turns out to be this same instance (one list for every instance, each in it) is left alone.
 *
 * @param key the key every instance of the mesh shares
 * @param nodeId this instance's name in the mesh, new for each process
 */
class ClusterMesh(
    private val key: ByteArray,
    val nodeId: String = UUID.randomUUID().toString(),
) : AutoCloseable {

    companion object {
        /** The route the other instances connect to. */
        const val PATH = "/cluster"

        /** The version of these messages. */
        const val PROTOCOL = 1

        /** The connection reached this same instance. */
        internal const val CLOSE_SELF = 4000

        /** The other end did not prove the key, or speaks another version. */
        internal const val CLOSE_REFUSED = 4003

        private const val PING_SECONDS = 20L
        private const val MAX_PENDING = 1000
        private const val LABEL = "wdc-store/cluster/v1"

        private val LOG = Log.getLogger("ClusterMesh")
        private val RANDOM = SecureRandom()
        private val GSON = Gson()

        /** The WebSocket address of a peer: `ws://` or `wss://`, with [PATH] when it names no path. */
        internal fun peerUri(address: String): URI {
            val uri = URI(address.trim())
            require(uri.scheme == "ws" || uri.scheme == "wss") { "Not a ws:// or wss:// address: $address" }
            return if (uri.path.isNullOrEmpty() || uri.path == "/") uri.resolve(PATH) else uri
        }

        private fun random(): String = ByteArray(24).also(RANDOM::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

        @Suppress("UNCHECKED_CAST")
        private fun parse(text: String): Map<String, Any?> = GSON.fromJson(text, Map::class.java) as Map<String, Any?>
    }

    private val handlers = ConcurrentHashMap<String, CopyOnWriteArrayList<(String) -> Unit>>()
    private val missedHandlers = CopyOnWriteArrayList<() -> Unit>()

    /** The connections other instances opened to this one, by WebSocket session. */
    private val incoming = ConcurrentHashMap<String, Incoming>()

    /** The connections this instance opens to the others. */
    private val links = CopyOnWriteArrayList<PeerLink>()

    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    private val pinger: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("cluster-ping").factory())

    @Volatile
    private var closed = false

    // :: Use

    /** Calls [handler] with the id of each message of [type] another instance sends. */
    fun on(type: String, handler: (id: String) -> Unit) {
        handlers.computeIfAbsent(type) { CopyOnWriteArrayList() } += handler
    }

    /** Calls [handler] each time another instance connects: it may have published something while it could not. */
    fun onMissed(handler: () -> Unit) {
        missedHandlers += handler
    }

    /** Tells every other instance; one not connected now gets it when it is. */
    fun publish(type: String, id: String) {
        val text = GSON.toJson(mapOf("type" to type, "id" to id))
        for (link in links) link.send(text)
    }

    /** The other instances whose connection to this one proved the key. */
    val connectedPeers: Set<String> get() = incoming.values.mapNotNull { it.peer }.toSet()

    /** The connections from this instance that proved the key at both ends. */
    val readyLinks: Int get() = links.count { it.ready }

    // :: Life

    /** Adds the route other instances connect to. */
    fun configure(config: JavalinConfig) {
        config.routes.ws(PATH) { ws ->
            ws.onConnect { ctx -> accept(ctx) }
            ws.onMessage { ctx -> receive(ctx) }
            ws.onClose { ctx -> incoming.remove(ctx.sessionId())?.peer?.let { LOG.info("Peer {} disconnected", it) } }
            ws.onError { ctx -> incoming.remove(ctx.sessionId()) }
        }
    }

    /** Opens, and keeps open, a connection to each of [peers] (`ws://host:port`; this instance among them is left alone). */
    fun start(peers: List<String>) {
        for (address in peers) {
            val link = PeerLink(peerUri(address))
            links += link
            link.start()
        }
        pinger.scheduleWithFixedDelay({ links.forEach { it.ping() } }, PING_SECONDS, PING_SECONDS, TimeUnit.SECONDS)
        LOG.info("Cluster node {}: connecting to {}", nodeId, peers.joinToString(", "))
    }

    override fun close() {
        closed = true
        pinger.shutdownNow()
        links.forEach { it.stop() }
        links.clear()
        http.shutdownNow()
    }

    // :: Proofs

    private fun prove(role: String, challenge: String, node: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        val bytes = mac.doFinal("$LABEL\n$role\n$challenge\n$node".toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun proven(proof: Any?, role: String, challenge: String, node: String): Boolean =
        proof is String && MessageDigest.isEqual(
            proof.toByteArray(StandardCharsets.UTF_8),
            prove(role, challenge, node).toByteArray(StandardCharsets.UTF_8),
        )

    // :: Connections from the others

    private class Incoming(val challenge: String) {
        @Volatile
        var peer: String? = null
    }

    private fun accept(ctx: WsConnectContext) {
        val challenge = random()
        incoming[ctx.sessionId()] = Incoming(challenge)
        ctx.send(GSON.toJson(mapOf("protocol" to PROTOCOL, "node" to nodeId, "challenge" to challenge)))
    }

    private fun receive(ctx: WsMessageContext) {
        val connection = incoming[ctx.sessionId()] ?: return refuse(ctx, "unknown connection")
        val message = try {
            parse(ctx.message())
        } catch (_: Exception) {
            return refuse(ctx, "not JSON")
        }

        val peer = connection.peer
        if (peer == null) {
            // the first message proves the key; nothing else is read before it
            val node = message["node"] as? String ?: return refuse(ctx, "no node")
            if ((message["protocol"] as? Number)?.toInt() != PROTOCOL) return refuse(ctx, "protocol ${message["protocol"]}")
            if (node == nodeId) {
                ctx.closeSession(CLOSE_SELF, "self")
                return
            }
            if (!proven(message["proof"], "client", connection.challenge, node)) return refuse(ctx, "wrong proof from $node")
            val theirs = message["challenge"] as? String ?: return refuse(ctx, "no challenge from $node")
            connection.peer = node
            ctx.send(GSON.toJson(mapOf("proof" to prove("server", theirs, nodeId))))
            LOG.info("Peer {} connected", node)
            // while it could not reach this instance, it published nothing here
            missedHandlers.forEach { runHandler("missed") { it() } }
            return
        }

        val type = message["type"] as? String ?: return
        val id = message["id"] as? String ?: return
        handlers[type]?.forEach { runHandler(type) { it(id) } }
    }

    private fun refuse(ctx: WsContext, why: String) {
        LOG.warn("Cluster connection refused: {}", why)
        incoming.remove(ctx.sessionId())
        ctx.closeSession(CLOSE_REFUSED, "refused")
    }

    private inline fun runHandler(type: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            LOG.error("Handling cluster message {}", type, e)
        }
    }

    // :: Connections to the others

    /**
     * The connection to one peer, opened again (1 s, doubling up to 30 s) whenever it drops. Messages go out in order,
     * from one thread; until both ends proved the key they wait (the last [MAX_PENDING]).
     */
    private inner class PeerLink(private val uri: URI) {

        @Volatile
        var ready = false
            private set

        @Volatile
        private var current: Connection? = null

        @Volatile
        private var stopped = false

        private val pending = ArrayDeque<String>()
        private var loop: Thread? = null
        private val sender: ExecutorService = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("cluster-send").factory())

        fun start() {
            loop = Thread.ofVirtual().name("cluster-link").start(::run)
        }

        fun stop() {
            stopped = true
            current?.socket?.abort()
            loop?.interrupt()
            sender.shutdownNow()
        }

        private fun run() {
            var delay = 1000L
            var failures = 0
            while (!closed && !stopped) {
                val connection = Connection()
                current = connection
                try {
                    http.newWebSocketBuilder().buildAsync(uri, connection).get(10, TimeUnit.SECONDS)
                    val code = connection.ended.get()
                    if (code == CLOSE_SELF) {
                        LOG.info("Cluster peer {} is this instance", uri)
                        return
                    }
                    if (code == CLOSE_REFUSED) LOG.warn("Cluster peer {} refused this instance: is the secret the same?", uri)
                    if (connection.wasReady) {
                        LOG.info("Cluster peer {} disconnected", uri)
                        delay = 1000L
                        failures = 0
                    }
                } catch (_: InterruptedException) {
                    return
                } catch (e: Exception) {
                    if (failures++ == 0) LOG.warn("Cluster peer {} not reachable, trying again: {}", uri, e.cause?.message ?: e.message)
                }
                synchronized(this) { ready = false }
                connection.socket?.abort()
                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {
                    return
                }
                delay = minOf(delay * 2, 30_000L)
            }
        }

        fun send(message: String) {
            synchronized(this) {
                if (!ready) {
                    pending.addLast(message)
                    if (pending.size > MAX_PENDING) pending.removeFirst()
                    return
                }
                val connection = current ?: return
                val ws = connection.socket ?: return
                sender.execute { transmit(connection, ws, message) }
            }
        }

        /** A send that fails ends the connection at once: the next messages wait for the new one. */
        private fun transmit(connection: Connection, ws: WebSocket, message: String) {
            try {
                ws.sendText(message, true).get(10, TimeUnit.SECONDS)
            } catch (e: Exception) {
                LOG.debug("Sending to cluster peer {}", uri, e)
                synchronized(this) { if (current === connection) ready = false }
                connection.end(-1)
            }
        }

        fun ping() {
            val connection = current ?: return
            val ws = connection.socket ?: return
            if (!ready) return
            if (System.currentTimeMillis() - connection.lastPong > 3 * PING_SECONDS * 1000) {
                LOG.warn("Cluster peer {} stopped answering", uri)
                connection.end(-1)
                return
            }
            sender.execute {
                try {
                    ws.sendPing(ByteBuffer.allocate(0)).get(10, TimeUnit.SECONDS)
                } catch (_: Exception) {
                    connection.end(-1)
                }
            }
        }

        /** One WebSocket to the peer, from opening to its end ([ended], with the close code; `-1` on failure). */
        private inner class Connection : WebSocket.Listener {
            val ended = CompletableFuture<Int>()
            val challenge = random()

            @Volatile
            var socket: WebSocket? = null

            @Volatile
            var lastPong = 0L

            @Volatile
            var wasReady = false

            private var serverNode = ""
            private val text = StringBuilder()

            fun end(code: Int) {
                ended.complete(code)
                socket?.abort()
            }

            override fun onOpen(webSocket: WebSocket) {
                socket = webSocket
                webSocket.request(1)
            }

            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                text.append(data)
                if (last) {
                    val message = text.toString()
                    text.setLength(0)
                    try {
                        answer(webSocket, parse(message))
                    } catch (e: Exception) {
                        LOG.warn("Cluster peer {}: unreadable message", uri, e)
                        end(-1)
                    }
                }
                webSocket.request(1)
                return null
            }

            private fun answer(webSocket: WebSocket, message: Map<String, Any?>) {
                if (message["challenge"] != null) {
                    // the peer's challenge: prove the key, and challenge it in turn
                    serverNode = message["node"] as? String ?: ""
                    if (serverNode == nodeId) return end(CLOSE_SELF)
                    val hello = mapOf(
                        "protocol" to PROTOCOL,
                        "node" to nodeId,
                        "proof" to prove("client", message["challenge"] as String, nodeId),
                        "challenge" to challenge,
                    )
                    sender.execute { transmit(this, webSocket, GSON.toJson(hello)) }
                } else if (message["proof"] != null && !wasReady) {
                    if (!proven(message["proof"], "server", challenge, serverNode)) {
                        LOG.warn("Cluster peer {} did not prove the key", uri)
                        return end(CLOSE_REFUSED)
                    }
                    synchronized(this@PeerLink) {
                        if (current !== this) return
                        wasReady = true
                        lastPong = System.currentTimeMillis()
                        ready = true
                        while (pending.isNotEmpty()) {
                            val waiting = pending.removeFirst()
                            sender.execute { transmit(this, webSocket, waiting) }
                        }
                    }
                    LOG.info("Cluster peer {} connected ({})", uri, serverNode)
                }
            }

            override fun onPong(webSocket: WebSocket, message: ByteBuffer): CompletionStage<*>? {
                lastPong = System.currentTimeMillis()
                webSocket.request(1)
                return null
            }

            override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String?): CompletionStage<*>? {
                ended.complete(statusCode)
                return null
            }

            override fun onError(webSocket: WebSocket, error: Throwable) {
                ended.complete(-1)
            }
        }
    }
}
