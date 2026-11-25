package br.com.wdc.store.backend.cluster

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.log.Slf4jLogFactory
import io.javalin.Javalin
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The instances' mesh, with real servers on free ports: they find each other from one list (each in it), tell each
 * other, refuse an instance with another key, and catch up a peer that comes back.
 */
class ClusterMeshTest {

    init {
        Log.setFactory(Slf4jLogFactory())
    }

    private class Node(val mesh: ClusterMesh, val server: Javalin) {
        val received = CopyOnWriteArrayList<String>()
        val missed = AtomicInteger()
        val address: String get() = "ws://127.0.0.1:${server.port()}"

        init {
            mesh.on("test") { received += it }
            mesh.onMissed { missed.incrementAndGet() }
        }

        fun stop() {
            mesh.close()
            server.stop()
        }
    }

    private val nodes = ArrayList<Node>()

    @AfterTest
    fun stopAll() = nodes.forEach { it.stop() }

    private fun node(key: ByteArray = ClusterKey.from("secret"), port: Int = 0): Node {
        val mesh = ClusterMesh(key)
        val server = Javalin.create { mesh.configure(it) }.start("127.0.0.1", port)
        return Node(mesh, server).also { nodes += it }
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < until) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("timed out waiting: $what")
    }

    @Test
    fun twoInstances_fromOneList_tellEachOther() {
        val a = node()
        val b = node()
        val peers = listOf(a.address, b.address)
        a.mesh.start(peers)
        b.mesh.start(peers)

        waitFor("both connected") { a.mesh.readyLinks == 1 && b.mesh.readyLinks == 1 }
        assertEquals(setOf(b.mesh.nodeId), a.mesh.connectedPeers, "only the other one, not itself")
        assertEquals(setOf(a.mesh.nodeId), b.mesh.connectedPeers)

        a.mesh.publish("test", "from-a")
        b.mesh.publish("test", "from-b")
        b.mesh.publish("other", "not-listened")

        waitFor("both received") { "from-a" in b.received && "from-b" in a.received }
        assertEquals(listOf("from-a"), b.received)
        assertEquals(listOf("from-b"), a.received, "nobody hears its own messages")
    }

    @Test
    fun anInstanceWithAnotherKey_isRefused_bothWays() {
        val a = node()
        val stranger = node(key = ClusterKey.from("another secret"))
        a.mesh.start(listOf(stranger.address))
        stranger.mesh.start(listOf(a.address))

        Thread.sleep(1500)
        assertEquals(0, a.mesh.readyLinks)
        assertEquals(0, stranger.mesh.readyLinks)
        assertEquals(emptySet(), a.mesh.connectedPeers)
        assertEquals(emptySet(), stranger.mesh.connectedPeers)

        stranger.mesh.publish("test", "intruder")
        Thread.sleep(300)
        assertEquals(emptyList(), a.received)
    }

    @Test
    fun aPeerThatComesBack_isConnectedAgain_andTakesItAsMissed() {
        val a = node()
        val b = node()
        val port = b.server.port()
        a.mesh.start(listOf(b.address))
        waitFor("a connected to b") { a.mesh.readyLinks == 1 }
        assertEquals(1, b.missed.get(), "a new connection: b may have missed what a sent before")

        b.stop()
        nodes -= b
        waitFor("a saw b go") { a.mesh.readyLinks == 0 }
        a.mesh.publish("test", "while-b-was-down")

        val b2 = node(port = port)
        waitFor("a connected to b again") { a.mesh.readyLinks == 1 }
        assertEquals(1, b2.missed.get())

        a.mesh.publish("test", "after")
        waitFor("b received") { "after" in b2.received }
        assertEquals(listOf("while-b-was-down", "after"), b2.received, "what was published while it was down waited, in order")
    }

    @Test
    fun aPeerAddress_isAWebSocket() {
        assertEquals("ws://store-b:8080/cluster", ClusterMesh.peerUri("ws://store-b:8080").toString())
        assertEquals("wss://store-b/cluster", ClusterMesh.peerUri(" wss://store-b/ ").toString())
        assertEquals("ws://store-b:8080/mesh", ClusterMesh.peerUri("ws://store-b:8080/mesh").toString())
        assertFailsWith<IllegalArgumentException> { ClusterMesh.peerUri("http://store-b:8080") }
        assertTrue(ClusterKey.from("x").contentEquals(ClusterKey.from("x")))
    }
}
