package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.domain.exception.BusinessException
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.exception.TransactionConflictException
import br.com.wdc.framework.domain.exception.TransactionLimitExceededException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Cabeçalho da transação remota corrente. */
const val TX_HEADER = "X-Tx-Id"

/** Cabeçalho que identifica a instância de cliente. */
const val CLIENT_HEADER = "X-Client-Id"

/** Um identificador novo para uma instância de transporte. */
@OptIn(ExperimentalUuidApi::class)
internal fun newClientId(): String = Uuid.random().toString()

/** O `X-Tx-Id` a enviar, se há transação remota corrente. */
internal fun HttpTransport.currentTxId(): String? = transactionIdSupplier?.invoke()?.takeIf { it.isNotBlank() }

/**
 * A exceção de uma resposta de erro: o status vira o mesmo tipo que o servidor lançou, para o chamador
 * tratar a recusa pelo que ela é. A mensagem é sempre `HTTP <status>: <corpo>`.
 */
internal fun httpFailure(status: Int, body: String? = null): RuntimeException {
    val message = if (body.isNullOrEmpty()) "HTTP $status" else "HTTP $status: $body"
    return when (status) {
        400 -> InvalidRequestException(message)
        403 -> AccessDeniedException(message)
        409 -> TransactionConflictException(message)
        429 -> TransactionLimitExceededException(message)
        else -> BusinessException(message)
    }
}
