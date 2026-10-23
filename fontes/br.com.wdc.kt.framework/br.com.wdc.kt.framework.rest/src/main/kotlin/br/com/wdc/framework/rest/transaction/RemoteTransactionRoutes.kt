package br.com.wdc.framework.rest.transaction

import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.persistence.transaction.RemoteTransactionCoordinator
import br.com.wdc.framework.rest.RawJson
import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.RestCall
import br.com.wdc.framework.rest.openapi.OperationDoc

/**
 * As rotas da transação remota dirigida pelo cliente, sobre o coordenador de [transactions]:
 *
 * - `POST <base>/begin` → `{"txId"}`;
 * - `POST <base>/commit`, `POST <base>/rollback` → `{"status": "committed" | "rolledback"}`;
 * - `GET <base>/status` → `{"status": "open" | "committed" | "rolledback" | "unknown"}`.
 *
 * O `txId` volta no cabeçalho `txHeader`; sem ele, 403. O dono é o `ownerKey` de [transactions], conferido pelo
 * coordenador em toda operação. Sem coordenador, as rotas respondem 500.
 */
object RemoteTransactionRoutes {

    fun register(
        routes: RestApi.Routes,
        transactions: RestTransactions,
        basePath: String = "/api/tx",
        doc: (operation: String) -> (OperationDoc.() -> Unit)? = { null },
    ) {
        fun coordinator(): RemoteTransactionCoordinator =
            transactions.coordinator ?: throw IllegalStateException("Coordenador de transação remota não inicializado")

        fun txId(call: RestCall): String =
            call.header(transactions.txHeader)?.takeIf { it.isNotBlank() } ?: throw AccessDeniedException("Cabeçalho ${transactions.txHeader} ausente")

        routes.post("$basePath/begin", doc = doc("begin")) { call ->
            RawJson.field("txId", coordinator().begin(transactions.ownerKey(call.request)))
        }
        routes.post("$basePath/commit", doc = doc("commit")) { call ->
            coordinator().commit(txId(call), transactions.ownerKey(call.request))
            RawJson.field("status", "committed")
        }
        routes.post("$basePath/rollback", doc = doc("rollback")) { call ->
            coordinator().rollback(txId(call), transactions.ownerKey(call.request))
            RawJson.field("status", "rolledback")
        }
        routes.get("$basePath/status", doc = doc("status")) { call ->
            RawJson.field("status", coordinator().status(txId(call), transactions.ownerKey(call.request)))
        }
    }
}
