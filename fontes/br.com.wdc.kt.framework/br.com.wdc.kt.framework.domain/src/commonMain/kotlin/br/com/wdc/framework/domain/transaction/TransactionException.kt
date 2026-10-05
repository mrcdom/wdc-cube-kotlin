package br.com.wdc.framework.domain.transaction

/** Base das falhas de demarcação de transação. */
open class TransactionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Propagação `mandatory` sem transação corrente. */
class TransactionRequiredException(message: String) : TransactionException(message)

/** Propagação `never` com transação corrente. */
class TransactionNotAllowedException(message: String) : TransactionException(message)

/** Falha ao abrir, confirmar ou desfazer a transação. */
class TransactionSystemException(message: String, cause: Throwable? = null) : TransactionException(message, cause)
