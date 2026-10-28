package br.com.wdc.framework.rest.transaction

import br.com.wdc.framework.domain.exception.TransactionConflictException
import br.com.wdc.framework.domain.transaction.TransactionService
import br.com.wdc.framework.persistence.transaction.RemoteTransactionCoordinator
import br.com.wdc.framework.rest.RequestContext

/**
 * A ponte entre a requisição e a transação: o que roda numa rota `transactional` confirma junto, ou nada
 * confirma.
 *
 * 1. Veio [txHeader] e há [coordinator]: a escrita se junta à transação remota do cliente e **não confirma** —
 *    a fronteira é do cliente.
 * 2. Não veio o cabeçalho, mas o dono tem transação remota aberta: o cabeçalho se perdeu no caminho. A escrita é
 *    recusada (409) em vez de confirmar sozinha, fora da transação. Desligue com [refuseWritesOutsideOpenTransaction].
 * 3. Senão: [service] `required` — uma transação por requisição. Sem serviço, executa direto.
 *
 * O cabeçalho é opcional: sem coordenador, ou sem transação remota aberta, a rota é simplesmente transacional.
 */
class RestTransactions {

    /** De onde vêm as transações locais, consultado a cada requisição. */
    var serviceSource: () -> TransactionService? = { null }

    /** De onde vem o coordenador das transações remotas, consultado a cada requisição. */
    var coordinatorSource: () -> RemoteTransactionCoordinator? = { null }

    /** As transações locais. `null` = a rota `transactional` executa sem transação. */
    var service: TransactionService?
        get() = serviceSource()
        set(value) {
            serviceSource = { value }
        }

    /** As transações remotas, dirigidas pelo cliente. `null` = não há. */
    var coordinator: RemoteTransactionCoordinator?
        get() = coordinatorSource()
        set(value) {
            coordinatorSource = { value }
        }

    var txHeader: String = "X-Tx-Id"

    /** Identifica a instância de cliente quando não há usuário autenticado. */
    var clientHeader: String = "X-Client-Id"

    var refuseWritesOutsideOpenTransaction: Boolean = true

    /**
     * A chave opaca do dono das transações remotas, em espaços de nome separados para um anônimo não se passar
     * por usuário: com principal, `user:<id>`; sem, `anon:<clientHeader>`, ou `null` se o cabeçalho não veio.
     */
    var ownerKey: (RequestContext) -> String? = { request ->
        request.principal?.let { "user:${it.id}" }
            ?: request.header(clientHeader)?.takeIf { it.isNotBlank() }?.let { "anon:$it" }
    }

    /** Executa [block] na transação que cabe à requisição (ver a classe). */
    suspend fun <T> run(request: RequestContext, block: suspend () -> T): T {
        val coordinator = coordinator
        if (coordinator != null) {
            val owner = ownerKey(request)
            val txId = request.header(txHeader)
            if (!txId.isNullOrBlank()) {
                coordinator.resume(txId, owner)
                try {
                    return block()
                } finally {
                    coordinator.suspend(txId)
                }
            }
            if (refuseWritesOutsideOpenTransaction && owner != null && coordinator.hasOpenTransactionForOwner(owner)) {
                throw TransactionConflictException(
                    "Escrita sem $txHeader enquanto há transação remota aberta para o solicitante — abortada para preservar a atomicidade"
                )
            }
        }
        val service = service ?: return block()
        return service.required { block() }
    }
}
