package br.com.wdc.store.persistence.client

import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.domain.exception.BusinessException
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.exception.NotFoundException
import br.com.wdc.framework.domain.exception.TransactionConflictException
import br.com.wdc.framework.domain.exception.TooManyRequestsException
import br.com.wdc.framework.domain.exception.UnauthenticatedException
import br.com.wdc.framework.domain.transaction.RemoteTransactionNotFoundException
import br.com.wdc.framework.domain.transaction.TransactionContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Header of the current remote transaction. */
const val TX_HEADER = "X-Tx-Id"

/** Header that identifies the client instance. */
const val CLIENT_HEADER = "X-Client-Id"

/** A new identifier for a transport instance. */
@OptIn(ExperimentalUuidApi::class)
internal fun newClientId(): String = Uuid.random().toString()

/**
 * The remote transaction of a coroutine, for the calls of one client: it goes with the coroutine that opened it
 * (and the ones it starts), so another action running meanwhile does not carry its id. [tx] `null` = no transaction
 * ([RestTransactionService] suspends the outer one this way). The client is told by its [HttpTransport.clientId] —
 * the identity the server knows it by —, so a transport that wraps another (to log, to measure) is the same client.
 */
internal class RemoteTransactionElement(
    val clientId: String,
    val txId: String?,
    val tx: TransactionContext?,
) : AbstractCoroutineContextElement(Key) {
    constructor(transport: HttpTransport, txId: String?, tx: TransactionContext?) : this(transport.clientId, txId, tx)

    companion object Key : CoroutineContext.Key<RemoteTransactionElement>
}

/** The current coroutine's remote transaction of this transport's client, if any. */
internal suspend fun HttpTransport.currentRemoteTransaction(): RemoteTransactionElement? =
    currentCoroutineContext()[RemoteTransactionElement]?.takeIf { it.clientId == clientId }

/** The `X-Tx-Id` to send, if there is a current remote transaction. */
internal suspend fun HttpTransport.currentTxId(): String? = currentRemoteTransaction()?.txId?.takeIf { it.isNotBlank() }

/**
 * Runs [block] with its calls of this transport inside the remote transaction [txId] (`null`: outside any), as
 * [RestTransactionService] does. For a test, or a caller that manages `/api/tx` by hand.
 */
suspend fun <T> HttpTransport.inRemoteTransaction(txId: String?, block: suspend () -> T): T =
    withContext(RemoteTransactionElement(this, txId, null)) { block() }

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
        401 -> UnauthenticatedException(message)
        403 -> AccessDeniedException(message)
        404 -> NotFoundException(message)
        409 -> TransactionConflictException(message)
        410 -> RemoteTransactionNotFoundException(message)
        // the open remote transactions, the auth requests of an address, the failed sign-ins of a user
        429 -> TooManyRequestsException(message)
        else -> BusinessException(message)
    }
}
