package br.com.wdc.framework.persistence.transaction

import br.com.wdc.framework.domain.transaction.AfterCommitTasks
import br.com.wdc.framework.domain.transaction.TransactionContext
import br.com.wdc.framework.domain.transaction.TransactionNotAllowedException
import br.com.wdc.framework.domain.transaction.TransactionRequiredException
import br.com.wdc.framework.domain.transaction.TransactionService
import br.com.wdc.framework.domain.transaction.TransactionSystemException
import javax.sql.DataSource
import kotlinx.coroutines.withContext

/**
 * CMT-style [TransactionService] implementation, over JDBC, reentrant.
 *
 * Built on the [TransactionScope] frame: REQUIRED joins the active transaction sharing the same connection;
 * REQUIRES_NEW opens a second connection and the current one stays untouched until the block ends;
 * NOT_SUPPORTED runs without a transaction and the current one comes back at the end.
 *
 * `work` semantics: COMMIT on normal return (only the owner commits), ROLLBACK on **any** exception
 * (rethrown intact — including `Error` and cancellation) or if [TransactionContext.setRollbackOnly] was
 * called. Failures opening or finishing the transaction become [TransactionSystemException].
 *
 * **A participant that fails dooms the transaction**, even if its caller catches the exception and carries on:
 * the owner rolls back and throws [TransactionSystemException] instead of returning the block's result, which
 * would report as done a write that was undone. Only an explicit [TransactionContext.setRollbackOnly] rolls back
 * without throwing.
 *
 * [afterCommit] stores the task in the owner of the physical transaction ([TransactionScope.afterCommit]); whoever
 * opened the owner runs it after the commit, with the connection already returned and no current transaction.
 *
 * **Coroutines.** `work` runs with the scope in the coroutine context ([TransactionScope.asContextElement]):
 * if it suspends and resumes on another thread, the current transaction goes along, with the same connection.
 * Even so, **the block must not really suspend** (`delay`, network wait): the transaction holds a pool
 * connection during the wait. The repositories' `suspend` implementations call blocking JDBC and respect this.
 *
 * @param dataSource DataSource of this context (per module), resolved on each opening — the composition root
 *                   injects it in the module bootstrap
 */
class TransactionServiceImpl(private val dataSource: () -> DataSource?) : TransactionService {

    override suspend fun <T> required(work: suspend (TransactionContext) -> T): T {
        val ambient = TransactionScope.current()
        return if (ambient != null) join(ambient, work) else runNew("Failed to open/join transaction", work)
    }

    override suspend fun <T> requiresNew(work: suspend (TransactionContext) -> T): T =
        runNew("Failed to open new transaction", work)

    override suspend fun <T> mandatory(work: suspend (TransactionContext) -> T): T {
        val ambient = TransactionScope.current()
            ?: throw TransactionRequiredException("mandatory: no active transaction in scope")
        return join(ambient, work)
    }

    override suspend fun <T> supports(work: suspend (TransactionContext) -> T): T {
        val ambient = TransactionScope.current()
        return if (ambient != null) join(ambient, work) else work(HANDLE)
    }

    override suspend fun <T> notSupported(work: suspend (TransactionContext) -> T): T =
        withTransaction(null) { work(HANDLE) }

    override suspend fun <T> never(work: suspend (TransactionContext) -> T): T {
        if (TransactionScope.isActive()) {
            throw TransactionNotAllowedException("never: there is an active transaction in scope")
        }
        return work(HANDLE)
    }

    override suspend fun afterCommit(task: suspend () -> Unit) {
        val current = TransactionScope.current()
        if (current != null) current.afterCommit(task) else AfterCommitTasks.runAll(listOf(task))
    }

    // :: Core

    /** Opens an owner transaction and runs `work` in it; the ambient transaction, if any, comes back at the end. */
    private suspend fun <T> runNew(openFailure: String, work: suspend (TransactionContext) -> T): T {
        val ds = dataSource() ?: throw TransactionSystemException("DataSource not initialized for this TransactionService")
        val scope = try {
            TransactionScope.open(ds)
        } catch (e: Exception) {
            throw TransactionSystemException(openFailure, e)
        }
        val result = withTransaction(scope) { finish(scope, work) }
        // committed and the connection is back in the pool: tasks run without a transaction, so as not to inherit one
        val tasks = scope.takeAfterCommit()
        if (tasks.isNotEmpty()) {
            withTransaction(null) { AfterCommitTasks.runAll(tasks) }
        }
        return result
    }

    /** Runs `work` as a participant of the ambient transaction. */
    private suspend fun <T> join(ambient: TransactionScope, work: suspend (TransactionContext) -> T): T =
        // the element guarantees the ambient transaction also when it was bound directly on the thread (remote coordinator)
        withTransaction(ambient) { finish(ambient.participant(), work) }

    /**
     * Runs the block with [scope] as the coroutine's current transaction (`null` = no transaction).
     *
     * The block's failure is caught **inside** `withContext` and rethrown outside: so the exception reaches the
     * caller as the same instance. Crossing the boundary as an exception, the coroutines debug mode (enabled
     * by `-ea`) would replace it with a copy, to rebuild the stack.
     */
    private suspend fun <T> withTransaction(scope: TransactionScope?, block: suspend () -> T): T {
        var failure: Throwable? = null
        val result = withContext(TransactionScope.asContextElement(scope)) {
            try {
                block()
            } catch (e: Throwable) {
                failure = e
                null
            }
        }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /**
     * Runs `work` in the frame: commits on success (only the owner commits; participant is a no-op), dooms the
     * transaction on exception (rethrown) and ends the frame. The owner of a doomed transaction throws after
     * rolling back.
     */
    private suspend fun <T> finish(scope: TransactionScope, work: suspend (TransactionContext) -> T): T {
        val result = try {
            work(HANDLE)
        } catch (e: Throwable) {
            scope.markFailed()
            try {
                scope.close()
            } catch (suppressed: Exception) {
                e.addSuppressed(suppressed)
            }
            throw e
        }
        try {
            scope.close()
        } catch (e: Exception) {
            throw TransactionSystemException("Failed to finish the transaction", e)
        }
        if (scope.ownsTransaction() && scope.isFailed()) {
            // a participant failed and the block carried on: it was rolled back, so the result is not a success
            throw TransactionSystemException(PARTICIPANT_FAILED)
        }
        return result
    }

    /** Handle given to `work`; reads the state of the ambient transaction. */
    private companion object {
        const val PARTICIPANT_FAILED = "The transaction was rolled back: a call inside it failed."
    }

    private object HANDLE : TransactionContext {

        override fun setRollbackOnly() {
            val current = TransactionScope.current()
                ?: throw IllegalStateException("setRollbackOnly: no active transaction in this scope")
            current.setRollbackOnly()
        }

        override fun isRollbackOnly(): Boolean = TransactionScope.current()?.isRollbackOnly() == true

        override fun isActive(): Boolean = TransactionScope.isActive()
    }
}
