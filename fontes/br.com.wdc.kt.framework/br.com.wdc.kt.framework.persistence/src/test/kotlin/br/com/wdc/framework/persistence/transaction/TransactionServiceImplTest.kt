package br.com.wdc.framework.persistence.transaction

import br.com.wdc.framework.domain.transaction.TransactionNotAllowedException
import br.com.wdc.framework.domain.transaction.TransactionRequiredException
import br.com.wdc.framework.domain.transaction.TransactionSystemException
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class TransactionServiceImplTest {

    private lateinit var db: TestDatabase
    private lateinit var tx: TransactionServiceImpl

    @BeforeTest
    fun setUp() {
        db = TestDatabase("tx-service-${System.nanoTime()}")
        tx = TransactionServiceImpl { db.dataSource }
    }

    @AfterTest
    fun tearDown() {
        // no test may leave a transaction stuck to the thread or a connection open
        assertNull(TransactionScope.current(), "transaction leaked to the thread")
        assertEquals(0, db.openConnections.get(), "connection not returned")
        db.close()
    }

    // :: REQUIRED

    @Test
    fun required_normalReturn_commits() = runBlocking {
        val result = tx.required { ctx ->
            assertTrue(ctx.isActive())
            assertFalse(ctx.isRollbackOnly())
            db.insertInCurrentTx(1)
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(listOf(1), db.ids())
    }

    @Test
    fun required_exception_rollsBackAndPropagatesTheSameInstance() = runBlocking {
        val boom = IllegalStateException("boom")
        val thrown = assertFailsWith<IllegalStateException> {
            tx.required { db.insertInCurrentTx(1); throw boom }
        }
        assertSame(boom, thrown)
        assertEquals(emptyList(), db.ids())
    }

    @Test
    fun required_error_alsoRollsBack() = runBlocking {
        // AssertionError is an Error, not an Exception — and it is what the app's presenters throw
        assertFailsWith<AssertionError> {
            tx.required { db.insertInCurrentTx(1); throw AssertionError("invariant") }
        }
        assertEquals(emptyList(), db.ids())
    }

    @Test
    fun required_setRollbackOnly_rollsBackWithoutException() = runBlocking {
        val result = tx.required { ctx ->
            db.insertInCurrentTx(1)
            ctx.setRollbackOnly()
            assertTrue(ctx.isRollbackOnly())
            "done"
        }
        assertEquals("done", result)
        assertEquals(emptyList(), db.ids())
    }

    @Test
    fun required_nested_sharesTheConnection() = runBlocking {
        tx.required {
            val outer = TransactionScope.current()!!.connection()
            db.insertInCurrentTx(1)
            tx.required {
                assertSame(outer, TransactionScope.current()!!.connection())
                db.insertInCurrentTx(2)
            }
            // the participant does not commit: still nothing visible from outside
            assertEquals(emptyList(), db.ids())
        }
        assertEquals(listOf(1, 2), db.ids())
        assertEquals(1, db.acquired.get())
    }

    @Test
    fun required_nestedFailure_marksTheOuterTransaction_evenIfCaught() = runBlocking {
        tx.required { ctx ->
            db.insertInCurrentTx(1)
            assertFailsWith<IllegalStateException> {
                tx.required { db.insertInCurrentTx(2); throw IllegalStateException("inner") }
            }
            assertTrue(ctx.isRollbackOnly())
        }
        assertEquals(emptyList(), db.ids())
    }

    @Test
    fun required_commitFailure_becomesTransactionSystemException() = runBlocking {
        db.failNextCommit = true
        val e = assertFailsWith<TransactionSystemException> { tx.required { db.insertInCurrentTx(1) } }
        assertEquals("commit refused (test)", e.cause?.message)
        assertEquals(emptyList(), db.ids())
    }

    @Test
    fun required_withoutDataSource_failsClearly() = runBlocking<Unit> {
        val unconfigured = TransactionServiceImpl { null }
        assertFailsWith<TransactionSystemException> { unconfigured.required { } }
    }

    // :: REQUIRES_NEW

    @Test
    fun requiresNew_commitsIndependently_onASecondConnection() = runBlocking {
        assertFailsWith<IllegalStateException> {
            tx.required {
                val outer = TransactionScope.current()!!.connection()
                db.insertInCurrentTx(1)
                tx.requiresNew {
                    assertNotSame(outer, TransactionScope.current()!!.connection())
                    db.insertInCurrentTx(2)
                }
                // the inner one already committed; the outer one is current again
                assertEquals(listOf(2), db.ids())
                assertSame(outer, TransactionScope.current()!!.connection())
                throw IllegalStateException("outer fails")
            }
        }
        assertEquals(listOf(2), db.ids())
        assertEquals(2, db.acquired.get())
    }

    @Test
    fun requiresNew_innerFailure_doesNotMarkTheOuter() = runBlocking {
        tx.required { ctx ->
            db.insertInCurrentTx(1)
            assertFailsWith<IllegalStateException> {
                tx.requiresNew { db.insertInCurrentTx(2); throw IllegalStateException("inner") }
            }
            assertFalse(ctx.isRollbackOnly())
        }
        assertEquals(listOf(1), db.ids())
    }

    // :: MANDATORY / NEVER / SUPPORTS / NOT_SUPPORTED

    @Test
    fun mandatory_withoutActiveTx_throws() = runBlocking<Unit> {
        assertFailsWith<TransactionRequiredException> { tx.mandatory { } }
        assertEquals(0, db.acquired.get())
    }

    @Test
    fun mandatory_withActiveTx_joins() = runBlocking {
        tx.required {
            tx.mandatory { db.insertInCurrentTx(1) }
        }
        assertEquals(listOf(1), db.ids())
        assertEquals(1, db.acquired.get())
    }

    @Test
    fun never_withActiveTx_throws() = runBlocking {
        tx.required {
            assertFailsWith<TransactionNotAllowedException> { tx.never { } }
        }
    }

    @Test
    fun never_withoutTx_runsWithoutTransaction() = runBlocking {
        val active = tx.never { ctx -> ctx.isActive() }
        assertFalse(active)
        assertEquals(0, db.acquired.get())
    }

    @Test
    fun supports_withoutTx_runsWithoutTransaction() = runBlocking {
        val active = tx.supports { ctx -> ctx.isActive() }
        assertFalse(active)
        assertEquals(0, db.acquired.get())
    }

    @Test
    fun supports_withTx_joins() = runBlocking {
        assertFailsWith<IllegalStateException> {
            tx.required {
                tx.supports { ctx -> assertTrue(ctx.isActive()); db.insertInCurrentTx(1) }
                throw IllegalStateException("outer fails")
            }
        }
        assertEquals(emptyList(), db.ids())
    }

    @Test
    fun notSupported_runsOutsideTheTransaction_andRestoresIt() = runBlocking {
        assertFailsWith<IllegalStateException> {
            tx.required {
                val outer = TransactionScope.current()
                db.insertInCurrentTx(1)
                tx.notSupported { ctx ->
                    assertFalse(ctx.isActive())
                    assertNull(TransactionScope.current())
                    db.insertAutoCommit(2)
                }
                assertSame(outer, TransactionScope.current())
                throw IllegalStateException("outer fails")
            }
        }
        // what was written outside the transaction survives its rollback
        assertEquals(listOf(2), db.ids())
    }

    @Test
    fun handle_setRollbackOnly_outsideTransaction_isIllegal() = runBlocking<Unit> {
        tx.supports { ctx ->
            assertFailsWith<IllegalStateException> { ctx.setRollbackOnly() }
            assertFalse(ctx.isRollbackOnly())
        }
    }

    // :: Coroutines

    @Test
    fun block_thatResumesOnAnotherThread_keepsTheSameConnection() {
        val first = Executors.newSingleThreadExecutor { Thread(it, "tx-first") }
        val second = Executors.newSingleThreadExecutor { Thread(it, "tx-second") }
        try {
            runBlocking(first.asCoroutineDispatcher()) {
                tx.required {
                    val startThread = Thread.currentThread()
                    val connection = TransactionScope.current()!!.connection()
                    db.insertInCurrentTx(1)

                    withContext(second.asCoroutineDispatcher()) {
                        assertTrue(Thread.currentThread().name.startsWith("tx-second"))
                        assertSame(connection, TransactionScope.current()!!.connection())
                        db.insertInCurrentTx(2)
                    }

                    delay(10) // real suspension, just to prove the resumption
                    assertSame(startThread, Thread.currentThread())
                    assertSame(connection, TransactionScope.current()!!.connection())
                    db.insertInCurrentTx(3)
                }
                assertNull(TransactionScope.current())
            }
            assertEquals(listOf(1, 2, 3), db.ids())
            assertEquals(1, db.acquired.get())
            // neither thread kept the transaction stuck
            assertNull(first.submit<TransactionScope?> { TransactionScope.current() }.get())
            assertNull(second.submit<TransactionScope?> { TransactionScope.current() }.get())
        } finally {
            first.shutdown()
            second.shutdown()
        }
    }

    @Test
    fun notSupported_staysOutsideTheTransaction_acrossAThreadSwitch() {
        val other = Executors.newSingleThreadExecutor { Thread(it, "tx-other") }
        try {
            runBlocking {
                tx.required {
                    tx.notSupported {
                        withContext(other.asCoroutineDispatcher()) { assertNull(TransactionScope.current()) }
                        delay(5)
                        assertNull(TransactionScope.current())
                    }
                    assertTrue(TransactionScope.isActive())
                }
            }
        } finally {
            other.shutdown()
        }
    }

    @Test
    fun independentCoroutines_doNotShareTransactions() = runBlocking {
        tx.required { db.insertInCurrentTx(1) }
        assertNull(TransactionScope.current())
        tx.required { db.insertInCurrentTx(2) }
        assertEquals(listOf(1, 2), db.ids())
        assertEquals(2, db.acquired.get())
    }
}
