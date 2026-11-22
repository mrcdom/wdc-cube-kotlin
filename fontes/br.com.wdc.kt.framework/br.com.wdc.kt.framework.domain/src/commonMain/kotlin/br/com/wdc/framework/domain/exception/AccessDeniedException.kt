package br.com.wdc.framework.domain.exception

/** The caller has no permission for the operation — the REST layer returns 403. */
open class AccessDeniedException(message: String) : RuntimeException(message)
