package br.com.wdc.framework.persistence.transaction

import br.com.wdc.framework.commons.log.Log
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/** [TransactionServiceImpl.afterCommit] e o commit de transação remota. */
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
        assertNull(TransactionScope.current(), "transação vazou para a thread")
        assertEquals(0, db.openConnections.get(), "conexão não devolvida")
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
            assertNull(seen, "a tarefa não roda dentro da transação")
        }
        assertEquals(listOf(1), seen, "a tarefa vê o que foi comitado")
        assertEquals(0, openDuringTask, "a conexão já foi devolvida")
        assertNull(transactionDuringTask, "a tarefa não herda a transação")
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
            assertEquals(emptyList(), ran, "só roda quando a de fora comita")
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
                assertEquals(listOf("new:[2]"), ran, "roda no fim da nova, antes de a de fora terminar")
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
                tx.afterCommit { throw IllegalStateException("tarefa falhou") }
                tx.afterCommit { ran += "next" }
                "ok"
            }
            assertEquals("ok", result, "a falha da tarefa não muda o resultado")
            assertEquals(listOf("next"), ran)
            assertEquals("tarefa falhou", logged.single()?.message)
        } finally {
            Log.setFactory(null)
        }
    }

    // :: transação remota

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
        assertEquals(emptyList(), ran, "espera o commit da transação remota")
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
