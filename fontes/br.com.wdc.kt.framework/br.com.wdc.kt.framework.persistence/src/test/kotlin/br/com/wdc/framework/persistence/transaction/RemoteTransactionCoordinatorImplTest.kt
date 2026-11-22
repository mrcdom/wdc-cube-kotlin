package br.com.wdc.framework.persistence.transaction

import br.com.wdc.framework.domain.transaction.RemoteTransactionNotFoundException

import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.domain.exception.TransactionConflictException
import br.com.wdc.framework.domain.exception.TransactionLimitExceededException
import br.com.wdc.framework.domain.transaction.TransactionSystemException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class RemoteTransactionCoordinatorImplTest {

    private lateinit var db: TestDatabase
    private val now = AtomicLong(1_000_000L)

    @BeforeTest
    fun setUp() {
        db = TestDatabase("tx-remote-${System.nanoTime()}")
    }

    @AfterTest
    fun tearDown() {
        assertNull(TransactionScope.current(), "transaction leaked to the thread")
        assertEquals(0, db.openConnections.get(), "connection not returned")
        db.close()
    }

    private fun coordinator(options: RemoteTransactionOptions = RemoteTransactionOptions.defaults()) =
        RemoteTransactionCoordinatorImpl({ db.dataSource }, options) { now.get() }

    /** A "request": rebinds the transaction, works and unbinds — on the same thread, as the REST host does. */
    private fun RemoteTransactionCoordinator.request(txId: String, owner: String?, work: () -> Unit) {
        resume(txId, owner)
        try {
            work()
        } finally {
            suspend(txId)
        }
    }

    /** Runs on another thread and returns the result (or rethrows the failure). */
    private fun <T> onAnotherThread(work: () -> T): T {
        val executor = Executors.newSingleThreadExecutor()
        try {
            return try {
                executor.submit<T> { work() }.get()
            } catch (e: java.util.concurrent.ExecutionException) {
                throw e.cause!!
            }
        } finally {
            executor.shutdown()
        }
    }

    // :: Basic cycle

    @Test
    fun commitAcrossThreads_persistsAllWrites() {
        val coordinator = coordinator()
        val txId = onAnotherThread { coordinator.begin("user:1") }
        assertTrue(coordinator.exists(txId))
        assertEquals("open", coordinator.status(txId, "user:1"))

        onAnotherThread { coordinator.request(txId, "user:1") { db.insertInCurrentTx(1) } }
        onAnotherThread { coordinator.request(txId, "user:1") { db.insertInCurrentTx(2) } }
        // nothing visible before the commit
        assertEquals(emptyList(), db.ids())

        onAnotherThread { coordinator.commit(txId, "user:1") }
        assertEquals(listOf(1, 2), db.ids())
        assertFalse(coordinator.exists(txId))
        assertEquals(1, db.acquired.get())
    }

    @Test
    fun rollbackAcrossThreads_discardsAllWrites() {
        val coordinator = coordinator()
        val txId = coordinator.begin("user:1")
        onAnotherThread { coordinator.request(txId, "user:1") { db.insertInCurrentTx(1) } }
        onAnotherThread { coordinator.rollback(txId, "user:1") }
        assertEquals(emptyList(), db.ids())
        assertEquals("rolledback", coordinator.status(txId, "user:1"))
    }

    @Test
    fun begin_doesNotAttachTheTransactionToTheCallingThread() {
        val coordinator = coordinator()
        val txId = coordinator.begin(null)
        assertNull(TransactionScope.current())
        coordinator.rollback(txId, null)
    }

    @Test
    fun transactionService_insideARequest_joinsTheRemoteTransaction() {
        val coordinator = coordinator()
        val service = TransactionServiceImpl { db.dataSource }
        val txId = coordinator.begin("user:1")

        coordinator.request(txId, "user:1") {
            runBlocking { service.required { db.insertInCurrentTx(1) } }
        }
        // required participated: it did not commit on its own
        assertEquals(emptyList(), db.ids())
        assertEquals(1, db.acquired.get())

        coordinator.commit(txId, "user:1")
        assertEquals(listOf(1), db.ids())
    }

    @Test
    fun failureInsideARequest_marksTheRemoteTransaction_soCommitRollsBack() {
        val coordinator = coordinator()
        val service = TransactionServiceImpl { db.dataSource }
        val txId = coordinator.begin(null)

        coordinator.request(txId, null) {
            assertFailsWith<IllegalStateException> {
                runBlocking { service.required { db.insertInCurrentTx(1); throw IllegalStateException("boom") } }
            }
        }
        // the commit rolls back and says so: whoever asked for the commit must not think it went through
        assertFailsWith<TransactionConflictException> { coordinator.commit(txId, null) }
        assertEquals(emptyList(), db.ids())
        assertEquals("rolledback", coordinator.status(txId, null))
        coordinator.rollback(txId, null) // the client, seeing the failure, sends rollback: idempotent
    }

    // :: Owner

    @Test
    fun ownerMismatch_isRejected_inEveryOperation() {
        val coordinator = coordinator()
        val txId = coordinator.begin("user:1")

        assertFailsWith<AccessDeniedException> { coordinator.resume(txId, "user:2") }
        assertFailsWith<AccessDeniedException> { coordinator.resume(txId, null) }
        assertFailsWith<AccessDeniedException> { coordinator.status(txId, "user:2") }
        assertFailsWith<AccessDeniedException> { coordinator.commit(txId, "user:2") }
        assertFailsWith<AccessDeniedException> { coordinator.rollback(txId, "anon:x") }
        // the refusal neither finishes nor locks the transaction
        assertEquals("open", coordinator.status(txId, "user:1"))

        coordinator.rollback(txId, "user:1")
        // the retained outcome is also protected by the owner
        assertFailsWith<AccessDeniedException> { coordinator.status(txId, "user:2") }
    }

    @Test
    fun ownerlessTransaction_acceptsAnyRequester() {
        val coordinator = coordinator()
        val txId = coordinator.begin(null)
        coordinator.request(txId, "user:9") { db.insertInCurrentTx(1) }
        coordinator.commit(txId, null)
        assertEquals(listOf(1), db.ids())
    }

    @Test
    fun hasOpenTransactionForOwner_tracksOpenTransactions() {
        val coordinator = coordinator()
        assertFalse(coordinator.hasOpenTransactionForOwner("user:1"))
        assertFalse(coordinator.hasOpenTransactionForOwner(null))

        val a = coordinator.begin("user:1")
        val b = coordinator.begin("user:1")
        assertTrue(coordinator.hasOpenTransactionForOwner("user:1"))
        assertFalse(coordinator.hasOpenTransactionForOwner("user:2"))

        coordinator.commit(a, "user:1")
        assertTrue(coordinator.hasOpenTransactionForOwner("user:1"))
        coordinator.rollback(b, "user:1")
        assertFalse(coordinator.hasOpenTransactionForOwner("user:1"))
    }

    // :: Concurrency

    @Test
    fun concurrentUseOfTheSameTxId_isRejected() {
        val coordinator = coordinator()
        val txId = coordinator.begin(null)

        coordinator.resume(txId, null)
        try {
            assertFailsWith<TransactionConflictException> { onAnotherThread { coordinator.resume(txId, null) } }
        } finally {
            coordinator.suspend(txId)
        }
        // once unbound, it can be used again
        onAnotherThread { coordinator.request(txId, null) { db.insertInCurrentTx(1) } }
        coordinator.commit(txId, null)
        assertEquals(listOf(1), db.ids())
    }

    @Test
    fun unknownTxId_isRefused() {
        val coordinator = coordinator()
        assertFailsWith<RemoteTransactionNotFoundException> { coordinator.resume("nope", null) }
        assertFailsWith<RemoteTransactionNotFoundException> { coordinator.commit("nope", null) }
        assertFailsWith<RemoteTransactionNotFoundException> { coordinator.rollback("nope", null) }
        coordinator.suspend("nope") // no-op
        assertFalse(coordinator.exists("nope"))
        assertFalse(coordinator.exists(null))
    }

    @Test
    fun rollbackWhileARequestUsesTheTransaction_waitsForTheRequestToRelease() {
        val coordinator = coordinator()
        val txId = coordinator.begin("user:1")

        // the running request (on this thread) holds the transaction; the client gives up and sends rollback
        coordinator.resume(txId, "user:1")
        try {
            db.insertInCurrentTx(1)
            onAnotherThread { coordinator.rollback(txId, "user:1") }
            assertEquals("rolledback", coordinator.status(txId, "user:1"), "the outcome already holds")
            assertFailsWith<RemoteTransactionNotFoundException> { onAnotherThread { coordinator.resume(txId, "user:1") } }
            // the connection was not closed under the request: it keeps working
            db.insertInCurrentTx(2)
            assertEquals(1, db.openConnections.get())
        } finally {
            coordinator.suspend(txId)
        }
        // on unbinding, the transaction closes rolled back
        assertEquals(emptyList(), db.ids())
        assertEquals(0, db.openConnections.get())
        coordinator.rollback(txId, "user:1") // repetition: idempotent
    }

    @Test
    fun commitWhileARequestUsesTheTransaction_isAConflict() {
        val coordinator = coordinator()
        val txId = coordinator.begin(null)
        coordinator.resume(txId, null)
        try {
            db.insertInCurrentTx(1)
            assertFailsWith<TransactionConflictException> { onAnotherThread { coordinator.commit(txId, null) } }
        } finally {
            coordinator.suspend(txId)
        }
        coordinator.commit(txId, null) // once released, the commit goes through
        assertEquals(listOf(1), db.ids())
    }

    // :: Caps

    @Test
    fun quotaKey_countsTheCap_whileTheOwnerKeyGuardsTheTransaction() {
        val coordinator = coordinator(RemoteTransactionOptions(maxOpenPerOwner = 2))
        val a = coordinator.begin("user:1/client:a", "user:1")
        val b = coordinator.begin("user:1/client:b", "user:1")
        // the cap is the user's: a third client of theirs cannot open
        assertFailsWith<TransactionLimitExceededException> { coordinator.begin("user:1/client:c", "user:1") }
        // the refusal of a write without txId is per owner (client instance)
        assertTrue(coordinator.hasOpenTransactionForOwner("user:1/client:a"))
        assertFalse(coordinator.hasOpenTransactionForOwner("user:1/client:c"))
        // another client of the same user cannot use the first one's transaction
        assertFailsWith<AccessDeniedException> { coordinator.resume(a, "user:1/client:b") }
        coordinator.rollback(a, "user:1/client:a")
        coordinator.begin("user:1/client:c", "user:1").also { coordinator.rollback(it, "user:1/client:c") }
        coordinator.rollback(b, "user:1/client:b")
    }

    @Test
    fun exceedingMaxOpen_isRejected() {
        val coordinator = coordinator(RemoteTransactionOptions(maxOpen = 2))
        val a = coordinator.begin("user:1")
        val b = coordinator.begin("user:2")
        assertFailsWith<TransactionLimitExceededException> { coordinator.begin("user:3") }
        assertEquals(2, db.acquired.get())

        coordinator.rollback(a, "user:1")
        val c = coordinator.begin("user:3") // a slot was freed
        coordinator.rollback(b, "user:2")
        coordinator.rollback(c, "user:3")
    }

    @Test
    fun exceedingMaxOpenPerOwner_isRejected_onlyForThatOwner() {
        val coordinator = coordinator(RemoteTransactionOptions(maxOpenPerOwner = 1))
        val a = coordinator.begin("user:1")
        assertFailsWith<TransactionLimitExceededException> { coordinator.begin("user:1") }
        val b = coordinator.begin("user:2")
        // null owners do not count toward the per-owner cap
        val c = coordinator.begin(null)
        val d = coordinator.begin(null)
        coordinator.rollback(a, "user:1")
        coordinator.rollback(b, "user:2")
        coordinator.rollback(c, null)
        coordinator.rollback(d, null)
    }

    // :: Reaper

    @Test
    fun idleTransaction_isReapedOnTheNextBegin() {
        val coordinator = coordinator(RemoteTransactionOptions(idleTimeoutMs = 1_000))
        val abandoned = coordinator.begin("user:1")
        coordinator.request(abandoned, "user:1") { db.insertInCurrentTx(1) }

        now.addAndGet(1_001)
        val fresh = coordinator.begin("user:2")

        assertFalse(coordinator.exists(abandoned))
        assertEquals("rolledback", coordinator.status(abandoned, "user:1"))
        assertFalse(coordinator.hasOpenTransactionForOwner("user:1"))
        assertEquals(emptyList(), db.ids())
        assertEquals(1, coordinator.stats().reaped)
        coordinator.rollback(fresh, "user:2")
    }

    @Test
    fun reapExpired_reclaimsAnAbandonedTransaction_withoutANewBegin() {
        val coordinator = coordinator(RemoteTransactionOptions(idleTimeoutMs = 1_000))
        val abandoned = coordinator.begin("user:1")
        coordinator.request(abandoned, "user:1") { db.insertInCurrentTx(1) }

        now.addAndGet(1_001)
        coordinator.reapExpired()

        assertFalse(coordinator.exists(abandoned))
        assertEquals("rolledback", coordinator.status(abandoned, "user:1"))
        assertEquals(0, db.openConnections.get(), "the abandoned transaction's connection is returned")
        assertEquals(1, coordinator.stats().reaped)
    }

    @Test
    fun startReaper_sweepsPeriodically_untilClosed() {
        val coordinator = coordinator(RemoteTransactionOptions(idleTimeoutMs = 1_000))
        val executor = Executors.newSingleThreadScheduledExecutor()
        try {
            val reaper = coordinator.startReaper(executor, intervalMs = 10)
            val abandoned = coordinator.begin("user:1")
            now.addAndGet(1_001)
            val deadline = System.currentTimeMillis() + 5_000
            while (coordinator.exists(abandoned) && System.currentTimeMillis() < deadline) Thread.sleep(10)
            assertFalse(coordinator.exists(abandoned), "the reaper rolled back the abandoned transaction")

            reaper.close()
            val afterClose = coordinator.begin("user:2")
            now.addAndGet(1_001)
            Thread.sleep(100)
            assertTrue(coordinator.exists(afterClose), "a closed reaper sweeps no more")
            coordinator.rollback(afterClose, "user:2")
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun usingTheTransaction_postponesTheIdleTimeout() {
        val coordinator = coordinator(RemoteTransactionOptions(idleTimeoutMs = 1_000))
        val txId = coordinator.begin(null)
        now.addAndGet(900)
        coordinator.request(txId, null) { }
        now.addAndGet(900)
        val other = coordinator.begin(null)
        assertTrue(coordinator.exists(txId))
        coordinator.rollback(txId, null)
        coordinator.rollback(other, null)
    }

    @Test
    fun absoluteLifetime_reapsLongLivedTransaction_evenIfKeptBusy() {
        val coordinator = coordinator(RemoteTransactionOptions(idleTimeoutMs = 1_000, maxLifetimeMs = 2_500))
        val txId = coordinator.begin(null)
        repeat(3) {
            now.addAndGet(900)
            coordinator.request(txId, null) { }
        }
        // never idle past the timeout, but past its lifetime
        val other = coordinator.begin(null)
        assertFalse(coordinator.exists(txId))
        assertEquals("rolledback", coordinator.status(txId, null))
        coordinator.rollback(other, null)
    }

    @Test
    fun transactionInUse_isNotReaped() {
        val coordinator = coordinator(RemoteTransactionOptions(idleTimeoutMs = 1_000))
        val txId = coordinator.begin(null)
        coordinator.resume(txId, null)
        try {
            now.addAndGet(5_000)
            val other = onAnotherThread { coordinator.begin(null) }
            assertTrue(coordinator.exists(txId))
            onAnotherThread { coordinator.rollback(other, null) }
        } finally {
            coordinator.suspend(txId)
        }
        coordinator.rollback(txId, null)
    }

    // :: Idempotency

    @Test
    fun commitIsIdempotent_andStatusReflectsOutcome() {
        val coordinator = coordinator()
        val txId = coordinator.begin("user:1")
        coordinator.request(txId, "user:1") { db.insertInCurrentTx(1) }

        coordinator.commit(txId, "user:1")
        coordinator.commit(txId, "user:1") // previous response lost: repeating is a no-op
        assertEquals("committed", coordinator.status(txId, "user:1"))
        assertEquals(listOf(1), db.ids())
        assertEquals(1, coordinator.stats().committed)
    }

    @Test
    fun rollbackIsIdempotent() {
        val coordinator = coordinator()
        val txId = coordinator.begin(null)
        coordinator.rollback(txId, null)
        coordinator.rollback(txId, null)
        assertEquals("rolledback", coordinator.status(txId, null))
        assertEquals(1, coordinator.stats().rolledBack)
    }

    @Test
    fun oppositeOutcome_conflicts() {
        val coordinator = coordinator()
        val committed = coordinator.begin(null)
        coordinator.commit(committed, null)
        assertFailsWith<TransactionConflictException> { coordinator.rollback(committed, null) }

        val rolledBack = coordinator.begin(null)
        coordinator.rollback(rolledBack, null)
        assertFailsWith<TransactionConflictException> { coordinator.commit(rolledBack, null) }
    }

    @Test
    fun reapedTransaction_countsAsRolledBack_forRetries() {
        val coordinator = coordinator(RemoteTransactionOptions(idleTimeoutMs = 1_000))
        val txId = coordinator.begin(null)
        now.addAndGet(2_000)
        val other = coordinator.begin(null)

        coordinator.rollback(txId, null) // same outcome: no-op
        assertFailsWith<TransactionConflictException> { coordinator.commit(txId, null) }
        coordinator.rollback(other, null)
    }

    @Test
    fun outcome_isForgottenAfterTheRetentionWindow() {
        val coordinator = coordinator(RemoteTransactionOptions(outcomeRetentionMs = 1_000))
        val txId = coordinator.begin(null)
        coordinator.commit(txId, null)
        assertEquals("committed", coordinator.status(txId, null))

        now.addAndGet(1_001)
        val other = coordinator.begin(null) // the sweep happens on begin
        assertEquals("unknown", coordinator.status(txId, null))
        assertFailsWith<RemoteTransactionNotFoundException> { coordinator.commit(txId, null) }
        coordinator.rollback(other, null)
    }

    @Test
    fun statusUnknown_forUnseenTxId() {
        assertEquals("unknown", coordinator().status("never-seen", "user:1"))
    }

    // :: Metrics and configuration

    @Test
    fun stats_reflectGaugesAndCounters() {
        val coordinator = coordinator(RemoteTransactionOptions(maxOpen = 3))
        val a = coordinator.begin("user:1")
        val b = coordinator.begin("user:1")
        val c = coordinator.begin("user:2")
        assertFailsWith<TransactionLimitExceededException> { coordinator.begin("user:3") }

        assertEquals(
            RemoteTransactionStats(
                openNow = 3, ownersWithOpen = 2, retainedOutcomes = 0,
                begun = 3, committed = 0, rolledBack = 0, reaped = 0, rejectedByLimit = 1,
            ),
            coordinator.stats(),
        )

        coordinator.commit(a, "user:1")
        coordinator.rollback(b, "user:1")
        coordinator.rollback(c, "user:2")

        assertEquals(
            RemoteTransactionStats(
                openNow = 0, ownersWithOpen = 0, retainedOutcomes = 3,
                begun = 3, committed = 1, rolledBack = 2, reaped = 0, rejectedByLimit = 1,
            ),
            coordinator.stats(),
        )
    }

    @Test
    fun withoutDataSource_beginFailsClearly() {
        val unconfigured = RemoteTransactionCoordinatorImpl({ null })
        assertFailsWith<TransactionSystemException> { unconfigured.begin(null) }
    }

    @Test
    fun options_fromConfig_readsSecondsAndFallsBackToDefaults() {
        val config = mapOf(
            "x.database.remoteTransaction.idleTimeoutSeconds" to 30,
            "x.database.remoteTransaction.maxOpen" to 7,
            "x.database.remoteTransaction.reapIntervalSeconds" to 5,
        )
        val options = RemoteTransactionOptions.fromConfig("x.") { key, default -> config[key] ?: default }

        assertEquals(30_000L, options.idleTimeoutMs)
        assertEquals(7, options.maxOpen)
        assertEquals(5_000L, options.reapIntervalMs)
        assertEquals(RemoteTransactionOptions.DEFAULT_MAX_LIFETIME_MS, options.maxLifetimeMs)
        assertEquals(RemoteTransactionOptions.DEFAULT_OUTCOME_RETENTION_MS, options.outcomeRetentionMs)
        assertEquals(RemoteTransactionOptions.DEFAULT_MAX_OPEN_PER_OWNER, options.maxOpenPerOwner)
        assertEquals(RemoteTransactionOptions.defaults(), RemoteTransactionOptions.fromConfig("") { _, d -> d })
    }
}
