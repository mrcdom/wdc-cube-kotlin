package br.com.wdc.store.test.transaction

import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.exception.TransactionConflictException
import br.com.wdc.framework.domain.exception.TooManyRequestsException
import br.com.wdc.framework.domain.transaction.TransactionNotAllowedException
import br.com.wdc.framework.domain.transaction.TransactionRequiredException
import br.com.wdc.framework.domain.transaction.TransactionSystemException
import br.com.wdc.framework.persistence.transaction.RemoteTransactionOptions
import br.com.wdc.store.domain.product.Product
import br.com.wdc.store.domain.product.ProductCriteria
import br.com.wdc.store.domain.purchase.Purchase
import br.com.wdc.store.domain.purchase.PurchaseCriteria
import br.com.wdc.store.domain.purchaseitem.PurchaseItem
import br.com.wdc.store.domain.purchaseitem.PurchaseItemCriteria
import br.com.wdc.store.domain.user.User
import br.com.wdc.store.persistence.client.HttpProductRepository
import br.com.wdc.store.persistence.client.OkHttpTransport
import br.com.wdc.store.persistence.client.RestTransactionService
import br.com.wdc.framework.domain.transaction.RemoteTransactionNotFoundException
import br.com.wdc.store.scripts.sgbd.DBReset
import br.com.wdc.store.test.util.RestTestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * The client-driven remote transaction: several HTTP writes take part in the same physical transaction on the
 * server (the same `txId`) and commit or roll back together.
 */
class RemoteTransactionRestTest {

    companion object {
        private val env = RestTestEnvironment(
            "wedocode-store-rest-remote-tx",
            remoteTransactionOptions = RemoteTransactionOptions(maxOpenPerOwner = 2),
        )

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private fun tx() = RestTransactionService(env.transport)

    private fun product(name: String) = Product().apply { this.name = name; price = 9.99; description = "$name desc" }

    private suspend fun exists(product: Product) = product.id != null && env.productRepo.fetchById(product.id!!) != null

    /** Another client, with its own identity, talking to the same server. */
    private fun anotherClient() = OkHttpTransport("http://127.0.0.1:${env.port}")

    private fun begin(transport: OkHttpTransport): String =
        Regex("\"txId\":\"([^\"]+)\"").find(transport.postJson("/api/tx/begin", "{}"))!!.groupValues[1]

    private fun finish(transport: OkHttpTransport, txId: String, outcome: String): String {
        transport.transactionIdSupplier = { txId }
        try {
            return transport.postJson("/api/tx/$outcome", "{}")
        } finally {
            transport.transactionIdSupplier = null
        }
    }

    // :: afterCommit

    @Test
    fun afterCommit_runsAfterTheRemoteCommit_withoutATransaction() = runBlocking {
        val tx = tx()
        val p = product("rtx-after-commit")
        val seen = ArrayList<String>()
        tx.required {
            env.productRepo.insert(p)
            tx.afterCommit {
                // outside the transaction: the read sees what was committed, and there is no current txId
                seen += "exists=${exists(p)}"
                tx.mandatory { } // without a current transaction, mandatory refuses
            }
            assertEquals(emptyList<String>(), seen)
        }
        assertEquals(listOf("exists=true"), seen, "the task failure (mandatory refused) only goes to the log")
    }

    @Test
    fun afterCommit_isDiscarded_onExceptionOrRollbackOnly() = runBlocking {
        val tx = tx()
        val ran = ArrayList<String>()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { tx.required { tx.afterCommit { ran += "x" }; throw IllegalStateException("boom") } }
        }
        tx.required { ctx -> tx.afterCommit { ran += "y" }; ctx.setRollbackOnly() }
        assertEquals(emptyList<String>(), ran)
    }

    @Test
    fun afterCommit_nestedRequired_waitsForTheOuter_requiresNewRunsOnItsOwn() = runBlocking {
        val tx = tx()
        val ran = ArrayList<String>()
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                tx.required {
                    tx.required { tx.afterCommit { ran += "inner" } }
                    tx.requiresNew { tx.afterCommit { ran += "new" } }
                    assertEquals(listOf("new"), ran)
                    throw IllegalStateException("boom")
                }
            }
        }
        assertEquals(listOf("new"), ran)
        tx.afterCommit { ran += "now" }
        assertEquals(listOf("new", "now"), ran)
    }

    // :: Outcome

    @Test
    fun commit_persistsAllWrites() = runBlocking {
        val p1 = product("rtx-commit-1")
        val p2 = product("rtx-commit-2")
        tx().required {
            env.productRepo.insert(p1)
            env.productRepo.insert(p2)
        }
        assertTrue(exists(p1))
        assertTrue(exists(p2))
    }

    @Test
    fun setRollbackOnly_discardsWrites() = runBlocking {
        val p = product("rtx-rollback-only")
        tx().required { t ->
            env.productRepo.insert(p)
            t.setRollbackOnly()
            assertTrue(t.isRollbackOnly())
        }
        assertFalse(exists(p))
    }

    @Test
    fun exception_rollsBackAllWritesAtomically_andKeepsItsType() = runBlocking {
        val p1 = product("rtx-atomic-1")
        val p2 = product("rtx-atomic-2")
        val e = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                tx().required {
                    env.productRepo.insert(p1)
                    env.productRepo.insert(p2)
                    error("boom")
                }
            }
        }
        assertEquals("boom", e.message)
        assertFalse(exists(p1))
        assertFalse(exists(p2))
    }

    @Test
    fun writeRefusedByTheServer_rollsBackWhatCameBefore() = runBlocking {
        // the purchase is written; the item, of a nonexistent product, fails on the server — nothing stays
        assertThrows(RuntimeException::class.java) {
            runBlocking {
                tx().required {
                    val purchase = Purchase().apply { buyDate = Clock.System.now(); user = User().apply { id = DBReset.FULANO_ID } }
                    assertTrue(env.purchaseRepo.insert(purchase))
                    env.purchaseItemRepo.insert(
                        PurchaseItem().apply {
                            this.purchase = purchase
                            product = Product().apply { id = 987_654L }
                            amount = 1
                            price = 10.5
                        }
                    )
                }
            }
        }
        assertEquals(2, env.purchaseRepo.count(PurchaseCriteria()))
        assertEquals(3, env.purchaseItemRepo.count(PurchaseItemCriteria()))
    }

    @Test
    fun requestThatFailsMidway_condemnsTheRemoteTransaction_evenIfTheClientCarriesOn() = runBlocking {
        val before = env.purchaseRepo.count(PurchaseCriteria())
        // a single request: the purchase is written, the second item (nonexistent product) fails on the server
        val purchase = Purchase().apply {
            buyDate = Clock.System.now()
            user = User().apply { id = DBReset.FULANO_ID }
            items = arrayListOf(
                PurchaseItem().apply { product = Product().apply { id = DBReset.COFFEE_MAKER_ID }; amount = 1; price = 10.5 },
                PurchaseItem().apply { product = Product().apply { id = 987_654L }; amount = 1; price = 10.5 },
            )
        }
        val e = assertThrows(TransactionConflictException::class.java) {
            runBlocking {
                tx().required {
                    try {
                        env.purchaseRepo.insert(purchase)
                    } catch (_: RuntimeException) {
                        // the client catches and carries on: the request's atomicity cannot depend on that
                    }
                }
            }
        }
        assertTrue(e.message!!.startsWith("HTTP 409"), e.message)
        assertEquals(before, env.purchaseRepo.count(PurchaseCriteria()), "the purchase written before the failure does not stay")
        assertTrue(env.productRepo.insert(product("rtx-after-condemned")), "the transaction was closed")
    }

    /** The environment's transport, but the commit gets lost on the way: before reaching the server, or on the way back. */
    private class LossyCommit(private val target: br.com.wdc.store.persistence.client.HttpTransport, private val loseRequest: Boolean) :
        br.com.wdc.store.persistence.client.HttpTransport by target {
        override fun postJson(path: String, body: String): String {
            if (path != "/api/tx/commit") return target.postJson(path, body)
            if (loseRequest) throw IllegalStateException("connection refused")
            target.postJson(path, body)
            throw IllegalStateException("read timed out") // committed on the server, but the response did not come back
        }
    }

    @Test
    fun commitWhoseResponseIsLost_isRecognisedAsCommitted() = runBlocking {
        val p = product("rtx-lost-response")
        var afterCommitRan = false
        val service = RestTransactionService(LossyCommit(env.transport, loseRequest = false))
        service.required {
            env.productRepo.insert(p)
            service.afterCommit { afterCommitRan = true }
        }
        assertTrue(exists(p), "stored on the server")
        assertTrue(afterCommitRan, "the client knows it committed: the afterCommit tasks run")
    }

    @Test
    fun commitThatNeverReachedTheServer_rollsBackAndFails() = runBlocking {
        val p = product("rtx-lost-request")
        val service = RestTransactionService(LossyCommit(env.transport, loseRequest = true))
        assertThrows(IllegalStateException::class.java) { runBlocking { service.required { env.productRepo.insert(p) } } }
        assertFalse(exists(p))
        assertTrue(env.productRepo.insert(product("rtx-after-lost-request")), "the transaction was rolled back, it did not stay open")
    }

    @Test
    fun everyKindOfWrite_joinsTheTransaction_includingTheImageUpload() = runBlocking {
        val id = DBReset.COFFEE_MAKER_ID
        val original = env.productRepo.fetchImage(id)!!
        tx().required { t ->
            assertTrue(env.productRepo.update(Product().apply { this.id = id; name = "another name" }, null, Product().apply { name = "~" }))
            assertTrue(env.productRepo.updateImage(id, byteArrayOf(1, 2, 3)))
            assertEquals(1, env.purchaseItemRepo.delete(PurchaseItemCriteria().withPurchaseItemId(DBReset.ADMIN_FIRST_PURCHASE_ITEM0_ID)))
            t.setRollbackOnly()
        }
        assertEquals("Italian", env.productRepo.fetchById(id)!!.name!!.substringBefore(' '))
        assertArrayEquals(original, env.productRepo.fetchImage(id))
        assertEquals(3, env.purchaseItemRepo.count(PurchaseItemCriteria()))
    }

    private fun getImage(id: Long, ifNoneMatch: String? = null): java.net.http.HttpResponse<ByteArray> {
        val builder = java.net.http.HttpRequest.newBuilder(java.net.URI("http://127.0.0.1:${env.port}/api/repo/product/$id/image"))
        ifNoneMatch?.let { builder.header("If-None-Match", it) }
        return java.net.http.HttpClient.newHttpClient().send(builder.GET().build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray())
    }

    @Test
    fun imageUploadInATransaction_isServedOnlyAfterTheCommit_andCachesRevalidate() = runBlocking {
        // fixed id, outside the sequence range: the server's image cache is static and survives the other
        // test environments of the same JVM, which would reuse a generated id (see the pitfalls in CLAUDE.md)
        val p = product("rtx-image").apply { id = 9_000_001L }
        assertTrue(env.productRepo.insert(p))
        val id = p.id!!
        assertTrue(env.productRepo.updateImage(id, byteArrayOf(1, 1, 1)))

        val first = getImage(id)
        assertEquals(200, first.statusCode())
        assertArrayEquals(byteArrayOf(1, 1, 1), first.body())
        val etag = first.headers().firstValue("ETag").orElse(null)
        assertNotNull(etag, "the image comes with an ETag")
        assertFalse(first.headers().firstValue("Cache-Control").orElse("").contains("immutable"), "the URL is mutable")
        assertEquals(304, getImage(id, etag).statusCode(), "the same image revalidates with 304")

        tx().required {
            assertTrue(env.productRepo.updateImage(id, byteArrayOf(2, 2, 2)))
            // before the commit, readers see the committed one — and it must not stay in the cache after the commit
            assertArrayEquals(byteArrayOf(1, 1, 1), getImage(id).body())
        }

        val after = getImage(id)
        assertArrayEquals(byteArrayOf(2, 2, 2), after.body(), "after the commit, the new image")
        assertNotEquals(etag, after.headers().firstValue("ETag").orElse(null))
        assertEquals(200, getImage(id, etag).statusCode(), "the old ETag no longer revalidates")
    }

    // :: The checkout, as a client application runs it: the purchase and its items in one remote transaction

    private fun purchaseOf(vararg products: Pair<Long, Int>) = Purchase().apply {
        user = User().apply { id = DBReset.FULANO_ID }
        buyDate = Clock.System.now()
        items = products.mapTo(ArrayList()) { (productId, amount) ->
            PurchaseItem().apply { product = Product().apply { id = productId }; this.amount = amount }
        }
    }

    @Test
    fun checkoutOverRest_commitsThePurchaseAndItsItems() = runBlocking {
        val purchase = purchaseOf(DBReset.COFFEE_MAKER_ID to 1, DBReset.FLASH_DRIVE_2GB_ID to 2)
        tx().required { assertTrue(env.purchaseRepo.insert(purchase)) }

        assertEquals(2, env.purchaseItemRepo.count(PurchaseItemCriteria().withPurchaseId(purchase.id)))
    }

    @Test
    fun checkoutOverRest_thatFailsInTheMiddle_leavesNoOrphanPurchase() = runBlocking {
        val purchase = purchaseOf(DBReset.COFFEE_MAKER_ID to 1, 987_654L to 1)
        assertThrows(RuntimeException::class.java) {
            runBlocking { tx().required { env.purchaseRepo.insert(purchase) } }
        }
        assertEquals(2, env.purchaseRepo.count(PurchaseCriteria()))
        assertEquals(3, env.purchaseItemRepo.count(PurchaseItemCriteria()))
        // the remote transaction was closed: the client can still write
        assertTrue(env.productRepo.insert(product("rtx-after-failed-checkout")))
    }

    // :: Propagation

    @Test
    fun nestedRequired_participatesInTheSameTransaction() = runBlocking {
        val service = tx()
        val p1 = product("rtx-nested-1")
        val p2 = product("rtx-nested-2")
        service.required { outer ->
            env.productRepo.insert(p1)
            service.required { inner ->
                assertSame(outer, inner)
                env.productRepo.insert(p2)
            }
            service.mandatory { assertTrue(it.isActive()) }
            outer.setRollbackOnly()
        }
        assertFalse(exists(p1))
        assertFalse(exists(p2))
    }

    @Test
    fun participantThatThrows_rollsBackAndTheOwnerThrows_evenIfTheCallerCarriesOn() = runBlocking {
        val service = tx()
        for (join in listOf<suspend (suspend () -> Unit) -> Unit>(
            { block -> service.required { block() } },
            { block -> service.mandatory { block() } },
            { block -> service.supports { block() } },
        )) {
            val before = product("rtx-participant-before")
            val inside = product("rtx-participant-inside")
            var condemned = false
            var afterCommitRan = false
            val e = assertThrows(TransactionSystemException::class.java) {
                runBlocking {
                    service.required { outer ->
                        env.productRepo.insert(before)
                        service.afterCommit { afterCommitRan = true }
                        try {
                            join { env.productRepo.insert(inside); throw IllegalStateException("participant failure") }
                        } catch (_: IllegalStateException) {
                            // the caller catches and carries on: the transaction is already doomed, as on the server
                        }
                        condemned = outer.isRollbackOnly()
                    }
                }
            }
            assertEquals("The transaction was rolled back: a call inside it failed.", e.message)
            assertTrue(condemned, "the participant that failed marks rollback-only")
            assertFalse(exists(before))
            assertFalse(exists(inside))
            assertFalse(afterCommitRan, "a rolled-back transaction runs no afterCommit task")
        }
    }

    @Test
    fun readsInsideTheTransaction_seeItsOwnWrites() = runBlocking {
        val service = tx()
        val p = product("rtx-read-own-write")
        service.required {
            env.productRepo.insert(p)
            // count, fetch, fetch-page and fetch-by-id all run in the transaction
            assertNotNull(env.productRepo.fetchById(p.id!!), "fetch-by-id sees the uncommitted insert")
            assertEquals(1, env.productRepo.count(ProductCriteria().withProductId(p.id)))
            assertEquals(listOf(p.id), env.productRepo.fetch(ProductCriteria().withProductId(p.id)).map { it.id })
            assertEquals(1, env.productRepo.fetchPage(ProductCriteria().withProductId(p.id), 0, 10).items.size)
            // another client still sees only what is committed
            assertFalse(HttpProductRepository(anotherClient()).fetchById(p.id!!) != null, "outside the transaction")
            it.setRollbackOnly()
        }
        assertFalse(exists(p))
    }

    @Test
    fun requiresNew_runsInItsOwnTransaction() = runBlocking {
        val service = tx()
        val outer = product("rtx-outer")
        val independent = product("rtx-independent")
        service.required { t ->
            env.productRepo.insert(outer)
            service.requiresNew { env.productRepo.insert(independent) }
            // the outer one is still in effect after the block
            service.mandatory { assertSame(t, it) }
            t.setRollbackOnly()
        }
        assertFalse(exists(outer))
        assertTrue(exists(independent))
    }

    @Test
    fun notSupported_insideARemoteTransaction_readsButCannotWrite() = runBlocking {
        // For the server, a write without the X-Tx-Id from someone with an open transaction is a lost header: it
        // refuses it. Suspending the remote transaction is therefore for reading — not for writing outside it.
        val service = tx()
        val loose = product("rtx-loose")
        service.required { t ->
            service.notSupported { ctx ->
                assertFalse(ctx.isActive())
                assertEquals(4, env.productRepo.count(ProductCriteria()))
                assertThrows(TransactionConflictException::class.java) { runBlocking { env.productRepo.insert(loose) } }
            }
            service.mandatory { assertSame(t, it) }
        }
        assertFalse(exists(loose))
        // outside any remote transaction, notSupported writes normally
        service.notSupported { env.productRepo.insert(loose) }
        assertTrue(exists(loose))
    }

    @Test
    fun mandatory_never_andSupports_followTheContract() = runBlocking<Unit> {
        val service = tx()
        assertThrows(TransactionRequiredException::class.java) { runBlocking { service.mandatory { } } }
        assertFalse(service.supports { it.isActive() })
        assertFalse(service.never { it.isActive() })
        assertThrows(IllegalStateException::class.java) { runBlocking { service.supports { it.setRollbackOnly() } } }
        service.required {
            assertTrue(service.supports { it.isActive() })
            assertThrows(TransactionNotAllowedException::class.java) { runBlocking { service.never { } } }
        }
    }

    // :: Server defenses

    @Test
    fun headerlessWrite_whileRemoteTxOpen_isRefusedWith409() = runBlocking {
        // The client opened the transaction but, due to a propagation failure, the write goes without the X-Tx-Id.
        // The server refuses it instead of committing it alone, outside the transaction.
        val client = anotherClient()
        val repo = HttpProductRepository(client)
        val txId = begin(client)
        try {
            val p = product("rtx-guard-orphan")
            val e = assertThrows(TransactionConflictException::class.java) { runBlocking { repo.insert(p) } }
            assertTrue(e.message!!.startsWith("HTTP 409"), e.message)
            assertEquals(0, env.productRepo.count(ProductCriteria().withName("rtx-guard-orphan")))
            // another client is not affected
            assertTrue(env.productRepo.insert(product("rtx-guard-other-client")))
        } finally {
            finish(client, txId, "rollback")
        }
        // once the transaction is closed, the client writes again
        assertTrue(repo.insert(product("rtx-guard-after")))
    }

    @Test
    fun transactionBelongsToWhoOpenedIt() = runBlocking<Unit> {
        val owner = anotherClient()
        val intruder = anotherClient()
        val txId = begin(owner)
        try {
            val e = assertThrows(AccessDeniedException::class.java) { finish(intruder, txId, "commit") }
            assertTrue(e.message!!.startsWith("HTTP 403"), e.message)

            intruder.transactionIdSupplier = { txId }
            assertThrows(AccessDeniedException::class.java) {
                runBlocking { HttpProductRepository(intruder).insert(product("rtx-intruder")) }
            }
        } finally {
            assertTrue("rolledback" in finish(owner, txId, "rollback"))
        }
        assertEquals(0, env.productRepo.count(ProductCriteria().withName("rtx-intruder")))
    }

    @Test
    fun outcomeIsIdempotent_andTheOppositeOneIsAConflict() {
        val client = anotherClient()
        val txId = begin(client)
        assertTrue("committed" in finish(client, txId, "commit"))
        assertTrue("committed" in finish(client, txId, "commit"))
        assertThrows(TransactionConflictException::class.java) { finish(client, txId, "rollback") }
    }

    @Test
    fun openTransactionsPerClient_areLimited_with429() {
        val client = anotherClient()
        val first = begin(client)
        val second = begin(client)
        try {
            val e = assertThrows(TooManyRequestsException::class.java) { begin(client) }
            assertTrue(e.message!!.startsWith("HTTP 429"), e.message)
        } finally {
            finish(client, first, "rollback")
            finish(client, second, "rollback")
        }
        // once the slots are freed, it opens again
        finish(client, begin(client), "rollback")
    }

    @Test
    fun outcomeWithoutTheHeader_isRefused() {
        // malformed request (400), not a refusal of access (403)
        assertThrows(InvalidRequestException::class.java) { anotherClient().postJson("/api/tx/commit", "{}") }
    }

    @Test
    fun status_tellsWhatHappenedToATransaction() {
        val client = anotherClient()
        fun status(txId: String): String {
            val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${env.port}/api/tx/status"))
                .header("X-Tx-Id", txId).header("X-Client-Id", client.clientId).GET().build()
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body()
        }

        val committed = begin(client)
        assertEquals("""{"status":"open"}""", status(committed))
        finish(client, committed, "commit")
        assertEquals("""{"status":"committed"}""", status(committed))

        val rolledBack = begin(client)
        finish(client, rolledBack, "rollback")
        assertEquals("""{"status":"rolledback"}""", status(rolledBack))
        assertEquals("""{"status":"unknown"}""", status("no-such-transaction"))
    }

    // :: Typed errors on the client

    @Test
    fun unknownOrExpiredTransaction_is410_andArrivesTyped() {
        val client = anotherClient()
        for (outcome in listOf("commit", "rollback")) {
            val e = assertThrows(RemoteTransactionNotFoundException::class.java) { finish(client, "no-such-transaction", outcome) }
            assertTrue(e.message!!.startsWith("HTTP 410"), e.message)
        }
        // a write carrying the txId of a transaction that no longer exists
        client.transactionIdSupplier = { "no-such-transaction" }
        try {
            assertThrows(RemoteTransactionNotFoundException::class.java) {
                client.postJson("/api/repo/product/insert", """{"name":"x","price":1.5,"description":"d"}""")
            }
        } finally {
            client.transactionIdSupplier = null
        }
    }

    @Test
    fun refusedRequests_arriveAsTheExceptionTheServerThrew() = runBlocking<Unit> {
        val e = assertThrows(InvalidRequestException::class.java) { runBlocking { env.productRepo.delete(ProductCriteria()) } }
        assertTrue(e.message!!.startsWith("HTTP 400"), e.message)
    }
}
