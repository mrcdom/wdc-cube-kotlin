package br.com.wdc.framework.rest.transaction

import br.com.wdc.framework.domain.exception.AccessDeniedException
import br.com.wdc.framework.persistence.transaction.RemoteTransactionCoordinator
import br.com.wdc.framework.rest.RawJson
import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.RestCall
import br.com.wdc.framework.rest.openapi.OperationDoc

/**
 * The routes of the client-driven remote transaction, over the coordinator of [transactions]:
 *
 * - `POST <base>/begin` → `{"txId"}`;
 * - `POST <base>/commit`, `POST <base>/rollback` → `{"status": "committed" | "rolledback"}`;
 * - `GET <base>/status` → `{"status": "open" | "committed" | "rolledback" | "unknown"}`.
 *
 * The `txId` comes back in the `txHeader` header; without it, 403. The owner is the `ownerKey` of [transactions],
 * checked by the coordinator on every operation. Without a coordinator, the routes answer 500.
 */
object RemoteTransactionRoutes {

    fun register(
        routes: RestApi.Routes,
        transactions: RestTransactions,
        basePath: String = "/api/tx",
        doc: (operation: String) -> (OperationDoc.() -> Unit)? = { null },
    ) {
        fun coordinator(): RemoteTransactionCoordinator =
            transactions.coordinator ?: throw IllegalStateException("Remote transaction coordinator not initialized")

        fun txId(call: RestCall): String =
            call.header(transactions.txHeader)?.takeIf { it.isNotBlank() } ?: throw AccessDeniedException("Missing ${transactions.txHeader} header")

        routes.post("$basePath/begin", doc = doc("begin")) { call ->
            RawJson.field("txId", coordinator().begin(transactions.ownerKey(call.request), transactions.quotaKey(call.request)))
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
