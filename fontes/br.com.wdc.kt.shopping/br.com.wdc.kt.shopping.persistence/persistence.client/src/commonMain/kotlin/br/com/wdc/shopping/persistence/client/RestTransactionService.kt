package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.util.AtomicRef
import br.com.wdc.framework.domain.transaction.AfterCommitTasks
import br.com.wdc.framework.domain.transaction.TransactionContext
import br.com.wdc.framework.domain.transaction.TransactionNotAllowedException
import br.com.wdc.framework.domain.transaction.TransactionRequiredException
import br.com.wdc.framework.domain.transaction.TransactionService
import br.com.wdc.framework.domain.transaction.TransactionSystemException

/**
 * The client's [TransactionService]: demarcates, over REST, a transaction that lives on the server.
 *
 * `required` opens the remote transaction (`POST /api/tx/begin` → `txId`); while the block runs, the transport
 * sends the `txId` in `X-Tx-Id` and writes join the same physical transaction on the server; at the end, `commit`
 * (normal return, unless `setRollbackOnly`) or `rollback` (exception). This is what makes several REST writes atomic.
 *
 * **A participant that fails dooms the transaction**, even if its caller catches the exception and carries on: the
 * owner rolls back and throws [TransactionSystemException] instead of returning the block's result. Only an explicit
 * `setRollbackOnly` rolls back without throwing. Same as the local transaction (`TransactionServiceImpl`).
 *
 * **The current transaction is one per instance** — that is, per client application —, not per thread: the
 * presentation's actions run serially and HTTP is blocking, so no two run at the same time. Hence the rule for
 * users: **the calls of a remote transaction are serialized**; a block that suspends waiting for something
 * other than its own HTTP leaves room for another action to inherit the `txId` (the server refuses concurrent
 * use of the same `txId` anyway).
 *
 * Reads made inside the block run in the transaction too (the transport sends the `txId` on them): they see what it
 * already wrote, and are not blocked by the rows it locked.
 *
 * `notSupported` inside a remote transaction is for **reading** outside it, not for writing: the server cannot
 * tell "suspended transaction" from "lost header" and refuses (409) a write without `X-Tx-Id` from someone who
 * has an open transaction. To write outside it, use `requiresNew`.
 *
 * [afterCommit] stores the task in the current remote transaction and runs it after the remote commit returns
 * successfully, with no current transaction; without a remote transaction, it runs right away.
 */
class RestTransactionService(private val transport: HttpTransport) : TransactionService {

    private val current = AtomicRef<TxState>()

    init {
        // the transport sends X-Tx-Id whenever there is a current remote transaction
        transport.transactionIdSupplier = { current.getOrNull()?.txId }
    }

    override suspend fun <T> required(work: suspend (TransactionContext) -> T): T {
        val existing = current.getOrNull()
        return if (existing != null) participate(existing, work) else runInNewTx(work)
    }

    override suspend fun <T> requiresNew(work: suspend (TransactionContext) -> T): T {
        // suspends the outer one: the block's calls do not carry its txId
        val outer = current.getOrNull()
        current.set(null)
        try {
            return runInNewTx(work)
        } finally {
            current.set(outer)
        }
    }

    override suspend fun <T> mandatory(work: suspend (TransactionContext) -> T): T {
        val state = current.getOrNull() ?: throw TransactionRequiredException("mandatory: no active remote transaction")
        return participate(state, work)
    }

    override suspend fun <T> supports(work: suspend (TransactionContext) -> T): T {
        val state = current.getOrNull() ?: return work(NoTx)
        return participate(state, work)
    }

    override suspend fun <T> notSupported(work: suspend (TransactionContext) -> T): T {
        val outer = current.getOrNull()
        current.set(null)
        try {
            return work(NoTx)
        } finally {
            current.set(outer)
        }
    }

    override suspend fun <T> never(work: suspend (TransactionContext) -> T): T {
        if (current.getOrNull() != null) {
            throw TransactionNotAllowedException("never: a remote transaction is active")
        }
        return work(NoTx)
    }

    override suspend fun afterCommit(task: suspend () -> Unit) {
        val state = current.getOrNull()
        if (state != null) state.afterCommit.add(task) else AfterCommitTasks.runAll(listOf(task))
    }

    /**
     * Runs [work] as a participant of the current remote transaction. A failure dooms the transaction, even if
     * the caller catches it and goes on: that is what the local transaction does (`TransactionServiceImpl`), and
     * without it the commit would confirm on the server what the participant wrote before failing.
     */
    private suspend fun <T> participate(state: TxState, work: suspend (TransactionContext) -> T): T {
        try {
            return work(state)
        } catch (e: Throwable) {
            state.failed = true
            throw e
        }
    }

    private suspend fun <T> runInNewTx(work: suspend (TransactionContext) -> T): T {
        val state = TxState(remoteBegin())
        current.set(state)
        val result: T
        try {
            result = try {
                work(state)
            } catch (e: Throwable) {
                // the block's failure is the one that matters
                rollbackQuietly()
                throw e
            }
            // the txId is still the current one: it goes in the commit/rollback header
            if (state.isRollbackOnly()) transport.postJson("/api/tx/rollback", "{}") else commit()
        } finally {
            current.set(null)
        }
        val tasks = state.afterCommit.drain()
        if (state.failed) {
            // a participant failed and the block carried on: it was rolled back, so the result is not a success
            throw TransactionSystemException(PARTICIPANT_FAILED)
        }
        // the remote commit returned successfully, and there is no current transaction anymore: a task that needs one opens its own
        if (!state.rollbackOnly) AfterCommitTasks.runAll(tasks)
        return result
    }

    /**
     * Requests the commit. A server response (success or refusal) is final. A transport failure does not tell
     * what happened — the commit may have arrived and the response been lost —: the client asks for the outcome
     * (`GET /api/tx/status`) before deciding. Committed: success. Still open: roll back and fail. Rolled back,
     * unknown or no answer: fail.
     */
    private fun commit() {
        try {
            transport.postJson("/api/tx/commit", "{}")
        } catch (e: Throwable) {
            if (e.isHttpAnswer()) {
                rollbackQuietly() // refused: a rollback of the same txId is accepted or harmless
                throw e
            }
            when (remoteStatus()) {
                "committed" -> return
                "open" -> rollbackQuietly()
            }
            throw e
        }
    }

    /** The outcome of the current transaction on the server, or `null` if not even that could be learned. */
    private fun remoteStatus(): String? = try {
        // getBytes is authenticated and carries X-Tx-Id in every transport
        val text = transport.getBytes("/api/tx/status")?.decodeToString() ?: return null
        val input = JsonInputFactory.createStringInput(text).input
        var status: String? = null
        input.beginObject()
        while (input.hasNext()) {
            if (input.nextName() == "status") status = input.nextString() else input.skipValue()
        }
        input.endObject()
        status
    } catch (_: Exception) {
        null
    }

    private fun rollbackQuietly() {
        try {
            transport.postJson("/api/tx/rollback", "{}")
        } catch (_: Exception) {
            // best effort: refused (already finished) or no network; the server rolls back the abandoned transaction
        }
    }

    private fun remoteBegin(): String {
        val input = JsonInputFactory.createStringInput(transport.postJson("/api/tx/begin", "{}")).input
        var txId: String? = null
        input.beginObject()
        while (input.hasNext()) {
            if (input.nextName() == "txId") txId = input.nextString() else input.skipValue()
        }
        input.endObject()
        if (txId.isNullOrBlank()) {
            throw TransactionSystemException("begin response without txId")
        }
        return txId
    }

    private companion object {
        const val PARTICIPANT_FAILED = "The transaction was rolled back: a call inside it failed."
    }

    /** An active remote transaction. */
    private class TxState(val txId: String) : TransactionContext {
        /** Rollback requested: rolled back without throwing. */
        var rollbackOnly = false

        /** Doomed by a participant that failed: rolled back, and the owner throws. */
        var failed = false
        val afterCommit = AfterCommitTasks()

        override fun setRollbackOnly() {
            rollbackOnly = true
        }

        override fun isRollbackOnly(): Boolean = rollbackOnly || failed

        override fun isActive(): Boolean = true
    }

    /** The context handed over when there is no transaction (`supports`, `notSupported`, `never`). */
    private object NoTx : TransactionContext {
        override fun setRollbackOnly(): Unit =
            throw IllegalStateException("setRollbackOnly: no active remote transaction in this scope")

        override fun isRollbackOnly(): Boolean = false

        override fun isActive(): Boolean = false
    }
}
