package br.com.wdc.framework.domain.resource

import br.com.wdc.framework.domain.transaction.TransactionContext
import br.com.wdc.framework.domain.transaction.TransactionService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceCallTest {

    /** Records whether the work ran inside `required`. */
    private class RecordingTransactions : TransactionService {
        var inside = false
        var opened = 0

        private val context = object : TransactionContext {
            override fun setRollbackOnly() {}
            override fun isRollbackOnly() = false
            override fun isActive() = inside
        }

        override suspend fun <T> required(work: suspend (TransactionContext) -> T): T {
            opened++
            inside = true
            try {
                return work(context)
            } finally {
                inside = false
            }
        }

        override suspend fun <T> requiresNew(work: suspend (TransactionContext) -> T): T = required(work)
        override suspend fun <T> mandatory(work: suspend (TransactionContext) -> T): T = required(work)
        override suspend fun <T> supports(work: suspend (TransactionContext) -> T): T = work(context)
        override suspend fun <T> notSupported(work: suspend (TransactionContext) -> T): T = work(context)
        override suspend fun <T> never(work: suspend (TransactionContext) -> T): T = work(context)
        override suspend fun afterCommit(task: suspend () -> Unit) = task()
    }

    private class Counter(var value: Int = 0)

    private class CounterQuery(private val counter: Counter, private val tx: RecordingTransactions) : ResourceQuery<Pair<Int, Boolean>>() {
        override suspend fun run() = counter.value to tx.inside
    }

    private class CounterIncrement(private val counter: Counter, private val tx: RecordingTransactions?, private val by: Int) :
        ResourceUpdate<Boolean>({ tx }) {
        override suspend fun update(): Boolean {
            counter.value += by
            return tx?.inside ?: false
        }
    }

    @Test
    fun aWrite_runsInsideATransaction_openedByRun() = runTest {
        val tx = RecordingTransactions()
        val counter = Counter()
        assertTrue(CounterIncrement(counter, tx, by = 2).run(), "inside the transaction")
        assertEquals(2, counter.value)
        assertEquals(1, tx.opened)
    }

    @Test
    fun aQuery_opensNoTransaction() = runTest {
        val tx = RecordingTransactions()
        val (value, inside) = CounterQuery(Counter(5), tx).run()
        assertEquals(5, value)
        assertFalse(inside)
        assertEquals(0, tx.opened)
    }

    @Test
    fun withoutATransactionService_theWriteRunsDirectly() = runTest {
        val counter = Counter()
        assertFalse(CounterIncrement(counter, null, by = 1).run())
        assertEquals(1, counter.value)
    }
}
