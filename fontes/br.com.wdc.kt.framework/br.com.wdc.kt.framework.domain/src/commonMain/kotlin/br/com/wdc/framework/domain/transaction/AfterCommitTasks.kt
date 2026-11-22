package br.com.wdc.framework.domain.transaction

import br.com.wdc.framework.commons.log.Log
import kotlin.coroutines.cancellation.CancellationException

/**
 * The queue of [TransactionService.afterCommit] tasks of a physical transaction, and the common way to run them.
 *
 * Implementations keep one queue per physical transaction, call [add] while it is open and, after the commit
 * (with the connection returned and outside the transaction), [run]. If the transaction is rolled back, just
 * discard the queue.
 *
 * It is not safe for concurrent use, nor does it need to be: a transaction is used by one coroutine at a time, and
 * the handoff from one thread to another is already ordered by whoever does it (the coroutine dispatcher, the
 * remote transaction registry).
 */
class AfterCommitTasks {

    private val tasks = ArrayList<suspend () -> Unit>()

    /** Appends a task to the end of the queue. */
    fun add(task: suspend () -> Unit) {
        tasks.add(task)
    }

    /** Empties the queue and returns what was there, in scheduling order. */
    fun drain(): List<suspend () -> Unit> = tasks.toList().also { tasks.clear() }

    /** Empties the queue and runs what was there (see [runAll]). */
    suspend fun run() = runAll(drain())

    companion object {
        /**
         * Runs the tasks in order. A failure goes to the log and doesn't stop the following ones: the commit has
         * already happened, and rethrowing would make the caller think the write failed. Coroutine cancellation is
         * rethrown.
         */
        suspend fun runAll(tasks: List<suspend () -> Unit>) {
            for (task in tasks) {
                try {
                    task()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.getLogger("AfterCommitTasks").error("A task scheduled for after the commit failed", e)
                }
            }
        }
    }
}
