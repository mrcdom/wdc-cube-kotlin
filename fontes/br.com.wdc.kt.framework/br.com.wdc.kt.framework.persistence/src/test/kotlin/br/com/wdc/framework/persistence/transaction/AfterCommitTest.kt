package br.com.wdc.framework.persistence.transaction

import br.com.wdc.framework.commons.log.Log
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/** [TransactionServiceImpl.afterCommit] and the commit of a remote transaction. */
class AfterCommitTest {

    private lateinit var db: TestDatabase
    private lateinit var tx: TransactionServiceImpl
    private val ran = ArrayList<String>()

    @BeforeTest
    fun setUp() {
        db = TestDatabase("tx-after-commit-${System.nanoTime()}")
        tx = TransactionServiceImpl { db.dataSource }
    }

    @AfterTest
    fun tearDown() {
        assertNull(TransactionScope.current(), "transaction leaked to the thread")
        assertEquals(0, db.openConnections.get(), "connection not returned")
        db.close()
    }

    @Test
    fun task_runsAfterTheCommit_outsideTheTransaction() = runBlocking {
        var seen: List<Int>? = null
        var openDuringTask = -1
        var transactionDuringTask: TransactionScope? = TransactionScope.current()
        tx.required {
            db.insertInCurrentTx(1)
            tx.afterCommit {
                seen = db.ids()
                openDuringTask = db.openConnections.get()
                transactionDuringTask = TransactionScope.current()
            }
            assertNull(seen, "the task does not run inside the transaction")
        }
        assertEquals(listOf(1), seen, "the task sees what was committed")
        assertEquals(0, openDuringTask, "the connection was already returned")
        assertNull(transactionDuringTask, "the task does not inherit the transaction")
    }

    @Test
    fun tasks_runInSchedulingOrder() = runBlocking {
        tx.required {
            tx.afterCommit { ran += "a" }
            tx.afterCommit { ran += "b" }
            tx.afterCommit { ran += "c" }
        }
        assertEquals(listOf("a", "b", "c"), ran)
    }

    @Test
    fun exception_discardsTheTask() = runBlocking {
        assertFailsWith<IllegalStateException> {
            tx.required {
                tx.afterCommit { ran += "x" }
                throw IllegalStateException("boom")
            }
        }
        assertEquals(emptyList(), ran)
    }

    @Test
    fun setRollbackOnly_discardsTheTask() = runBlocking {
        tx.required { ctx ->
            db.insertInCurrentTx(1)
            tx.afterCommit { ran += "x" }
            ctx.setRollbackOnly()
        }
        assertEquals(emptyList(), ran)
        assertEquals(emptyList(), db.ids())
    }

    @Test
    fun nestedRequired_handsTheTaskToTheOuterTransaction() = runBlocking {
        tx.required {
            tx.required { tx.afterCommit { ran += "inner" } }
            assertEquals(emptyList(), ran, "runs only when the outer one commits")
            tx.supports { tx.afterCommit { ran += "supports" } }
            assertEquals(emptyList(), ran)
        }
        assertEquals(listOf("inner", "supports"), ran)
    }

    @Test
    fun nestedRequired_outerRolledBack_discardsTheInnerTask() = runBlocking {
        assertFailsWith<IllegalStateException> {
            tx.required {
                tx.required { tx.afterCommit { ran += "inner" } }
                throw IllegalStateException("boom")
            }
        }
        assertEquals(emptyList(), ran)
    }

    @Test
    fun requiresNew_runsWhenItCommits_evenIfTheOuterIsRolledBack() = runBlocking {
        assertFailsWith<IllegalStateException> {
            tx.required {
                db.insertInCurrentTx(1)
                tx.requiresNew {
                    db.insertInCurrentTx(2)
                    tx.afterCommit { ran += "new:${db.ids()}" }
                }
                assertEquals(listOf("new:[2]"), ran, "runs at the end of the new one, before the outer one ends")
                throw IllegalStateException("boom")
            }
        }
        assertEquals(listOf("new:[2]"), ran)
        assertEquals(listOf(2), db.ids())
    }

    @Test
    fun withoutTransaction_runsAtOnce() = runBlocking {
        tx.afterCommit { ran += "now" }
        assertEquals(listOf("now"), ran)
        tx.required {
            tx.notSupported { tx.afterCommit { ran += "notSupported" } }
            assertEquals(listOf("now", "notSupported"), ran)
        }
        tx.never { tx.afterCommit { ran += "never" } }
        assertEquals(listOf("now", "notSupported", "never"), ran)
    }

    @Test
    fun failingTask_isLogged_andTheNextOneStillRuns() = runBlocking {
        val logged = ArrayList<Throwable?>()
        Log.setFactory { name -> Log(name).also { it.errorOut = { _, t -> logged += t } } }
        try {
            val result = tx.required {
                tx.afterCommit { throw IllegalStateException("task failed") }
                tx.afterCommit { ran += "next" }
                "ok"
            }
            assertEquals("ok", result, "the task failure does not change the result")
            assertEquals(listOf("next"), ran)
            assertEquals("task failed", logged.single()?.message)
        } finally {
            Log.setFactory(null)
        }
    }

    // :: remote transaction

    @Test
    fun remoteTransaction_runsTasksOnCommit_andDiscardsOnRollback() {
        val coordinator = RemoteTransactionCoordinatorImpl({ db.dataSource }, RemoteTransactionOptions.defaults()) { 0L }

        val committed = coordinator.begin(null)
        coordinator.resume(committed, null)
        try {
            runBlocking { tx.required { db.insertInCurrentTx(1); tx.afterCommit { ran += "commit:${db.ids()}" } } }
        } finally {
            coordinator.suspend(committed)
        }
        assertEquals(emptyList(), ran, "waits for the remote transaction commit")
        coordinator.commit(committed, null)
        assertEquals(listOf("commit:[1]"), ran)

        val rolledBack = coordinator.begin(null)
        coordinator.resume(rolledBack, null)
        try {
            runBlocking { tx.required { tx.afterCommit { ran += "rollback" } } }
        } finally {
            coordinator.suspend(rolledBack)
        }
        coordinator.rollback(rolledBack, null)
        assertEquals(listOf("commit:[1]"), ran)
    }
}
