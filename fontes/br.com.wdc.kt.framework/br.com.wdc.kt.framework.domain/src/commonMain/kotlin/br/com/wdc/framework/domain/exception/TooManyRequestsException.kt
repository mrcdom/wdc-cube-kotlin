package br.com.wdc.framework.domain.exception

/**
 * The caller asked too often, or failed too often: it must wait before trying again — the REST layer returns 429,
 * with `Retry-After` when [retryAfterSeconds] is known. A client gets it for any 429.
 */
open class TooManyRequestsException(
    message: String,
    /** The seconds to wait before trying again, when known. */
    val retryAfterSeconds: Long? = null,
) : RuntimeException(message)
