package br.com.wdc.framework.domain.exception

/** The operation conflicts with the state of a remote transaction — the REST layer returns 409. */
open class TransactionConflictException(message: String) : RuntimeException(message)

/** Cap of open remote transactions reached — the REST layer returns 429. */
open class TransactionLimitExceededException(message: String) : RuntimeException(message)
