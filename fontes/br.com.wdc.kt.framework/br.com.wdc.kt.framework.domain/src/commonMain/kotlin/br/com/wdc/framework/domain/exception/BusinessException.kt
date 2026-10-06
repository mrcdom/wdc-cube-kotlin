package br.com.wdc.framework.domain.exception

/** Falha de regra de negócio. Base das exceções que o chamador deve tratar como erro do pedido. */
open class BusinessException : RuntimeException {

    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)

    companion object {
        /** Devolve [e] se já for [BusinessException]; senão cria uma nova, com [e] como *suppressed*. */
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

/** Pedido malformado ou recusado por validação — a camada REST devolve 400. */
open class InvalidRequestException(message: String) : BusinessException(message)

/** A operação exigia o servidor e ele não está acessível. */
open class OfflineException(cause: Throwable?) : BusinessException(cause)
