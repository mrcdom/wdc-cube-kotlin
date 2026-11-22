package br.com.wdc.framework.domain.transaction

/** Control of the current transaction, handed to the transactional block. */
interface TransactionContext {

    /** Marks the transaction for rollback (irreversibly). [IllegalStateException] if there is no active transaction. */
    fun setRollbackOnly()

    fun isRollbackOnly(): Boolean

    /** `true` when the block is running inside a transaction. */
    fun isActive(): Boolean
}
