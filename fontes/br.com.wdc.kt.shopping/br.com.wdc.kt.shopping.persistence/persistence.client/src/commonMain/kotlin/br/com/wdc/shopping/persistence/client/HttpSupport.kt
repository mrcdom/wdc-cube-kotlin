package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.domain.exception.BusinessException
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.exception.NotFoundException
import br.com.wdc.framework.domain.exception.TransactionConflictException
import br.com.wdc.framework.domain.exception.TransactionLimitExceededException
import br.com.wdc.framework.domain.transaction.RemoteTransactionNotFoundException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Header of the current remote transaction. */
const val TX_HEADER = "X-Tx-Id"

/** Header that identifies the client instance. */
const val CLIENT_HEADER = "X-Client-Id"

/** A new identifier for a transport instance. */
@OptIn(ExperimentalUuidApi::class)
internal fun newClientId(): String = Uuid.random().toString()

/** The `X-Tx-Id` to send, if there is a current remote transaction. */
internal fun HttpTransport.currentTxId(): String? = transactionIdSupplier?.invoke()?.takeIf { it.isNotBlank() }

/**
 * The exception for an error response: the status becomes the same type the server threw, so the caller
 * handles the refusal for what it is. The message is always `HTTP <status>: <body>`.
 */
/**
 * `true` when the failure is a server response (it came from [httpFailure], which always writes "HTTP <status>"),
 * not a transport failure — connection refused, timeout —, where it is unknown what the server did.
 */
internal fun Throwable.isHttpAnswer(): Boolean = message?.startsWith("HTTP ") == true

internal fun httpFailure(status: Int, body: String? = null): RuntimeException {
    val message = if (body.isNullOrEmpty()) "HTTP $status" else "HTTP $status: $body"
    return when (status) {
        400 -> InvalidRequestException(message)
        403 -> AccessDeniedException(message)
        404 -> NotFoundException(message)
        409 -> TransactionConflictException(message)
        410 -> RemoteTransactionNotFoundException(message)
        429 -> TransactionLimitExceededException(message)
        else -> BusinessException(message)
    }
}
