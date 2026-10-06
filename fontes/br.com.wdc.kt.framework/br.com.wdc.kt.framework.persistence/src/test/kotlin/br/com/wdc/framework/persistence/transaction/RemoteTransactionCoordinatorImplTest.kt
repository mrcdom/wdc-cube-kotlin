package br.com.wdc.framework.persistence.transaction

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
        assertNull(TransactionScope.current(), "transação vazou para a thread")
        assertEquals(0, db.openConnections.get(), "conexão não devolvida")
        db.close()
    }

    private fun coordinator(options: RemoteTransactionOptions = RemoteTransactionOptions.defaults()) =
        RemoteTransactionCoordinatorImpl({ db.dataSource }, options) { now.get() }

    /** Uma "requisição": religa a transação, trabalha e desliga — na mesma thread, como o host REST faz. */
    private fun RemoteTransactionCoordinator.request(txId: String, owner: String?, work: () -> Unit) {
        resume(txId, owner)
        try {
            work()
        } finally {
            suspend(txId)
        }
    }

    /** Executa em outra thread e devolve o resultado (ou relança a falha). */
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

    // :: Ciclo básico

    @Test
    fun commitAcrossThreads_persistsAllWrites() {
        val coordinator = coordinator()
        val txId = onAnotherThread { coordinator.begin("user:1") }
        assertTrue(coordinator.exists(txId))
        assertEquals("open", coordinator.status(txId, "user:1"))

        onAnotherThread { coordinator.request(txId, "user:1") { db.insertInCurrentTx(1) } }
        onAnotherThread { coordinator.request(txId, "user:1") { db.insertInCurrentTx(2) } }
        // nada visível antes do commit
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
        // required participou: não comitou por conta própria
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
        coordinator.commit(txId, null)
        assertEquals(emptyList(), db.ids())
    }

    // :: Dono

    @Test
    fun ownerMismatch_isRejected_inEveryOperation() {
        val coordinator = coordinator()
        val txId = coordinator.begin("user:1")

        assertFailsWith<AccessDeniedException> { coordinator.resume(txId, "user:2") }
        assertFailsWith<AccessDeniedException> { coordinator.resume(txId, null) }
        assertFailsWith<AccessDeniedException> { coordinator.status(txId, "user:2") }
        assertFailsWith<AccessDeniedException> { coordinator.commit(txId, "user:2") }
        assertFailsWith<AccessDeniedException> { coordinator.rollback(txId, "anon:x") }
        // a recusa não finaliza nem prende a transação
        assertEquals("open", coordinator.status(txId, "user:1"))

        coordinator.rollback(txId, "user:1")
        // o desfecho retido também é protegido pelo dono
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

    // :: Concorrência

    @Test
    fun concurrentUseOfTheSameTxId_isRejected() {
        val coordinator = coordinator()
        val txId = coordinator.begin(null)

        coordinator.resume(txId, null)
        try {
            assertFailsWith<TransactionSystemException> { onAnotherThread { coordinator.resume(txId, null) } }
        } finally {
            coordinator.suspend(txId)
        }
        // depois de desligada, volta a poder ser usada
        onAnotherThread { coordinator.request(txId, null) { db.insertInCurrentTx(1) } }
        coordinator.commit(txId, null)
        assertEquals(listOf(1), db.ids())
    }

    @Test
    fun unknownTxId_isRefused() {
        val coordinator = coordinator()
        assertFailsWith<TransactionSystemException> { coordinator.resume("nope", null) }
        assertFailsWith<TransactionSystemException> { coordinator.commit("nope", null) }
        assertFailsWith<TransactionSystemException> { coordinator.rollback("nope", null) }
        coordinator.suspend("nope") // no-op
        assertFalse(coordinator.exists("nope"))
        assertFalse(coordinator.exists(null))
    }

    // :: Tetos

    @Test
    fun exceedingMaxOpen_isRejected() {
        val coordinator = coordinator(RemoteTransactionOptions(maxOpen = 2))
        val a = coordinator.begin("user:1")
        val b = coordinator.begin("user:2")
        assertFailsWith<TransactionLimitExceededException> { coordinator.begin("user:3") }
        assertEquals(2, db.acquired.get())

        coordinator.rollback(a, "user:1")
        val c = coordinator.begin("user:3") // liberou uma vaga
        coordinator.rollback(b, "user:2")
        coordinator.rollback(c, "user:3")
    }

    @Test
    fun exceedingMaxOpenPerOwner_isRejected_onlyForThatOwner() {
        val coordinator = coordinator(RemoteTransactionOptions(maxOpenPerOwner = 1))
        val a = coordinator.begin("user:1")
        assertFailsWith<TransactionLimitExceededException> { coordinator.begin("user:1") }
        val b = coordinator.begin("user:2")
        // donos nulos não entram no teto por dono
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
        // nunca ficou ociosa além do timeout, mas passou do tempo de vida
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

    // :: Idempotência

    @Test
    fun commitIsIdempotent_andStatusReflectsOutcome() {
        val coordinator = coordinator()
        val txId = coordinator.begin("user:1")
        coordinator.request(txId, "user:1") { db.insertInCurrentTx(1) }

        coordinator.commit(txId, "user:1")
        coordinator.commit(txId, "user:1") // resposta anterior perdida: repetir é no-op
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

        coordinator.rollback(txId, null) // mesmo desfecho: no-op
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
        val other = coordinator.begin(null) // a varredura acontece no begin
        assertEquals("unknown", coordinator.status(txId, null))
        assertFailsWith<TransactionSystemException> { coordinator.commit(txId, null) }
        coordinator.rollback(other, null)
    }

    @Test
    fun statusUnknown_forUnseenTxId() {
        assertEquals("unknown", coordinator().status("never-seen", "user:1"))
    }

    // :: Métricas e configuração

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
        )
        val options = RemoteTransactionOptions.fromConfig("x.") { key, default -> config[key] ?: default }

        assertEquals(30_000L, options.idleTimeoutMs)
        assertEquals(7, options.maxOpen)
        assertEquals(RemoteTransactionOptions.DEFAULT_MAX_LIFETIME_MS, options.maxLifetimeMs)
        assertEquals(RemoteTransactionOptions.DEFAULT_OUTCOME_RETENTION_MS, options.outcomeRetentionMs)
        assertEquals(RemoteTransactionOptions.DEFAULT_MAX_OPEN_PER_OWNER, options.maxOpenPerOwner)
        assertEquals(RemoteTransactionOptions.defaults(), RemoteTransactionOptions.fromConfig("") { _, d -> d })
    }
}
