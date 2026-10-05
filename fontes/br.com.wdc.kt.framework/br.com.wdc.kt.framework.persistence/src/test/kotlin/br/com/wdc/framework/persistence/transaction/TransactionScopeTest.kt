package br.com.wdc.framework.persistence.transaction

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

class TransactionScopeTest {

    private lateinit var db: TestDatabase

    @BeforeTest
    fun setUp() {
        db = TestDatabase("tx-scope-${System.nanoTime()}")
    }

    @AfterTest
    fun tearDown() {
        assertNull(TransactionScope.current(), "transação vazou para a thread")
        assertEquals(0, db.openConnections.get(), "conexão não devolvida")
        db.close()
    }

    @Test
    fun beginNew_installsAnOwner_andCloseCommits() {
        val scope = TransactionScope.beginNew(db.dataSource)
        assertSame(scope, TransactionScope.current())
        assertTrue(TransactionScope.isActive())
        assertFalse(scope.connection().autoCommit)
        db.insertInCurrentTx(1)
        scope.close()
        assertEquals(listOf(1), db.ids())
    }

    @Test
    fun begin_withActiveTransaction_returnsAParticipant() {
        val owner = TransactionScope.begin(db.dataSource)
        val participant = TransactionScope.begin(db.dataSource)
        assertNotSame(owner, participant)
        assertSame(owner.connection(), participant.connection())
        assertEquals(1, db.acquired.get())

        db.insertInCurrentTx(1)
        participant.close()
        // fechar o participante não comita nem tira o owner da thread
        assertSame(owner, TransactionScope.current())
        assertEquals(emptyList(), db.ids())

        owner.close()
        assertEquals(listOf(1), db.ids())
    }

    @Test
    fun setRollbackOnly_onParticipant_marksTheOwner() {
        val owner = TransactionScope.begin(db.dataSource)
        val participant = TransactionScope.begin(db.dataSource)
        db.insertInCurrentTx(1)
        participant.setRollbackOnly()
        assertTrue(owner.isRollbackOnly())
        participant.close()
        owner.close()
        assertEquals(emptyList(), db.ids())
    }

    @Test
    fun suspend_keepsTheTransactionAlive_andResumeRestoresIt() {
        val first = TransactionScope.beginNew(db.dataSource)
        db.insertInCurrentTx(1)

        val suspended = TransactionScope.suspend()
        assertSame(first, suspended)
        assertNull(TransactionScope.current())

        val second = TransactionScope.beginNew(db.dataSource)
        assertNotSame(first.connection(), second.connection())
        db.insertInCurrentTx(2)
        second.close()
        assertEquals(listOf(2), db.ids())

        TransactionScope.resume(suspended)
        assertSame(first, TransactionScope.current())
        first.close()
        assertEquals(listOf(1, 2), db.ids())
    }

    @Test
    fun suspend_withoutTransaction_isNull_andResumeNullIsNoOp() {
        assertNull(TransactionScope.suspend())
        TransactionScope.resume(null)
        assertNull(TransactionScope.current())
    }

    @Test
    fun close_isIdempotent_andConnectionIsRefusedAfterwards() {
        val scope = TransactionScope.beginNew(db.dataSource)
        scope.close()
        scope.close()
        assertFailsWith<IllegalStateException> { scope.connection() }
        assertEquals(1, db.acquired.get())
    }

    @Test
    fun close_ofASuspendedScope_doesNotDisturbTheCurrentOne() {
        val first = TransactionScope.beginNew(db.dataSource)
        val suspended = TransactionScope.suspend()!!
        val second = TransactionScope.beginNew(db.dataSource)

        suspended.close()
        assertSame(second, TransactionScope.current())

        second.close()
        assertSame(first, suspended)
    }

    @Test
    fun close_restoresAutoCommit_beforeReturningTheConnection() {
        val scope = TransactionScope.beginNew(db.dataSource)
        val connection = scope.connection()
        scope.setRollbackOnly()
        scope.close()
        assertTrue(connection.isClosed)
    }

    @Test
    fun commitFailure_rollsBack_andReleasesTheConnection() {
        val scope = TransactionScope.beginNew(db.dataSource)
        db.insertInCurrentTx(1)
        db.failNextCommit = true
        assertFailsWith<java.sql.SQLException> { scope.close() }
        assertEquals(emptyList(), db.ids())
    }
}
