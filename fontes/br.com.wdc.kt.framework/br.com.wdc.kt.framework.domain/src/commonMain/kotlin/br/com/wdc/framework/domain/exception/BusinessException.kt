package br.com.wdc.framework.domain.exception

/** Business rule failure. Base of the exceptions the caller must treat as a request error. */
open class BusinessException : RuntimeException {

    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)

    companion object {
        /** Returns [e] if it already is a [BusinessException]; otherwise creates a new one, with [e] as *suppressed*. */
        fun wrap(message: String, e: Exception): BusinessException {
            if (e is BusinessException) {
                return e
            }
            val exn = BusinessException(message)
            exn.addSuppressed(e)
            return exn
        }
    }
}

/** Malformed request or one rejected by validation — the REST layer returns 400. */
open class InvalidRequestException(message: String) : BusinessException(message)

/** The operation required the server and it is not reachable. */
open class OfflineException(cause: Throwable?) : BusinessException(cause)
