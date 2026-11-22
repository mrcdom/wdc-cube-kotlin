package br.com.wdc.framework.domain.transaction

/** Base of transaction demarcation failures. */
open class TransactionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** `mandatory` propagation without a current transaction. */
class TransactionRequiredException(message: String) : TransactionException(message)

/** `never` propagation with a current transaction. */
class TransactionNotAllowedException(message: String) : TransactionException(message)

/**
 * The remote transaction doesn't exist (anymore): the `txId` was never opened, or was already rolled back for being
 * abandoned, or its outcome has already left retention. The REST layer returns 410; the client receives it with the
 * same type.
 */
class RemoteTransactionNotFoundException(message: String) : TransactionException(message)

/** Failure to open, commit or roll back the transaction. */
class TransactionSystemException(message: String, cause: Throwable? = null) : TransactionException(message, cause)
