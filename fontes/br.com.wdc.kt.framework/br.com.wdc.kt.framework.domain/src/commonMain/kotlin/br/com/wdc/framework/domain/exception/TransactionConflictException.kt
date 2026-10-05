package br.com.wdc.framework.domain.exception

/** A operação conflita com o estado de uma transação remota — a camada REST devolve 409. */
open class TransactionConflictException(message: String) : RuntimeException(message)

/** Teto de transações remotas abertas atingido — a camada REST devolve 429. */
open class TransactionLimitExceededException(message: String) : RuntimeException(message)
