package br.com.wdc.framework.domain.transaction

/** Controle da transação corrente, entregue ao bloco transacional. */
interface TransactionContext {

    /** Marca a transação para rollback (sem volta). [IllegalStateException] se não há transação ativa. */
    fun setRollbackOnly()

    fun isRollbackOnly(): Boolean

    /** `true` quando o bloco está executando dentro de uma transação. */
    fun isActive(): Boolean
}
