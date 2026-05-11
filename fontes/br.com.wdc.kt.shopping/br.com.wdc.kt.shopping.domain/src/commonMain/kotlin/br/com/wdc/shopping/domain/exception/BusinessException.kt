package br.com.wdc.shopping.domain.exception

open class BusinessException : RuntimeException {

    constructor() : super()

    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(cause: Throwable) : super(cause)

    companion object {
        fun wrap(message: String, e: Exception): BusinessException {
            if (e is BusinessException) return e
            val exn = BusinessException(message)
            exn.addSuppressed(e)
            return exn
        }
    }
}
