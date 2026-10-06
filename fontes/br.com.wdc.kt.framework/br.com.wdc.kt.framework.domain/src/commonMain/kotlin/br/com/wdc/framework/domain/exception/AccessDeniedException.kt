package br.com.wdc.framework.domain.exception

/** O chamador não tem permissão para a operação — a camada REST devolve 403. */
open class AccessDeniedException(message: String) : RuntimeException(message)
