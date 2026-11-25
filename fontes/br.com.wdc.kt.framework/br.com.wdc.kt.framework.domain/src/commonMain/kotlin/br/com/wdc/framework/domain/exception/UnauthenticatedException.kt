package br.com.wdc.framework.domain.exception

/**
 * The request has no valid credential: none was given, or the one given was refused (a wrong password, an expired
 * session). A client gets it for a 401.
 */
open class UnauthenticatedException(message: String) : RuntimeException(message)
