package br.com.wdc.framework.domain.transaction

/**
 * CMT-style transaction demarcation: the use case declares the propagation and the service takes care of
 * opening, committing and rolling back.
 *
 * In every propagation: **normal return → commit** (unless [TransactionContext.setRollbackOnly]);
 * **any exception → rollback, and the original exception is rethrown intact**; failure while finishing →
 * [TransactionSystemException].
 *
 * The contract is the same on the server (JDBC transaction) and on the client (remote transaction via REST).
 *
 * **The transactional block must not really suspend** — `delay`, dispatcher switch, waiting on network
 * unrelated to the transaction. On the server the transaction holds a pool connection during the wait; on the
 * client, the calls of a remote transaction must be serialized. The `suspend` implementations of the
 * repositories respect this.
 */
interface TransactionService {

    /** Joins the current transaction; if there is none, opens one. */
    suspend fun <T> required(work: suspend (TransactionContext) -> T): T

    /** Suspends the current transaction, if any, and runs in a new transaction. */
    suspend fun <T> requiresNew(work: suspend (TransactionContext) -> T): T

    /** Requires a current transaction; without one, [TransactionRequiredException]. */
    suspend fun <T> mandatory(work: suspend (TransactionContext) -> T): T

    /** Joins the current transaction if there is one; otherwise runs without a transaction. */
    suspend fun <T> supports(work: suspend (TransactionContext) -> T): T

    /** Suspends the current transaction, if any, and runs without a transaction. */
    suspend fun <T> notSupported(work: suspend (TransactionContext) -> T): T

    /** Requires no transaction; with one, [TransactionNotAllowedException]. */
    suspend fun <T> never(work: suspend (TransactionContext) -> T): T

    /**
     * Schedules [task] for after the commit of the current transaction: it runs only if the transaction commits, in
     * scheduling order, already outside it (the connection returned). Rolled back (exception or
     * [TransactionContext.setRollbackOnly]), the task is discarded. Without a current transaction, it runs right away.
     * A task failure goes to the log and affects neither the transaction result nor the following tasks.
     *
     * The task belongs to the **physical** transaction: inside a [required] or [supports] that joins another, it
     * waits for the outer one's commit; inside a [requiresNew], it runs when that one commits, even if the outer one
     * is rolled back later. In [notSupported] and [never] there is no transaction: it runs right away. When it runs,
     * the task is in no transaction at all; if it needs one, it opens its own.
     *
     * [AfterCommitTasks] is the common executor for the implementations.
     */
    suspend fun afterCommit(task: suspend () -> Unit)
}
