package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.persistence.transaction.RemoteTransactionCoordinator
import br.com.wdc.shopping.domain.exception.AccessDeniedException
import br.com.wdc.shopping.domain.security.SecurityContextHolder
import io.javalin.config.JavalinConfig
import io.javalin.http.Context

/**
 * Endpoints da transação remota dirigida pelo cliente: `begin`, `commit`, `rollback` e `status`.
 *
 * O cliente abre a transação (`begin` → `txId`), envia o `txId` em [TX_HEADER] nas escritas seguintes — que
 * se juntam à mesma transação física no servidor — e encerra com `commit` ou `rollback`.
 *
 * O dono da transação é verificado pelo coordenador em toda operação: outro solicitante não consegue usá-la.
 * Com a segurança ligada, o filtro de `/api/tx/` exige autenticação.
 */
object TxApiController {

    const val TX_HEADER = "X-Tx-Id"

    /**
     * Identificador opaco da instância de cliente. É o dono da transação quando não há usuário autenticado
     * (segurança desligada) — assim toda transação tem um dono distinguível, mesmo anônima.
     */
    const val CLIENT_HEADER = "X-Client-Id"

    fun configure(config: JavalinConfig) {
        config.routes.post("/api/tx/begin", ::begin)
        config.routes.post("/api/tx/commit", ::commit)
        config.routes.post("/api/tx/rollback", ::rollback)
        config.routes.get("/api/tx/status", ::status)
    }

    private fun begin(ctx: Context) {
        ctx.jsonField("txId", coordinator().begin(currentOwnerKey(ctx)))
    }

    private fun commit(ctx: Context) {
        coordinator().commit(txIdHeader(ctx), currentOwnerKey(ctx))
        ctx.jsonField("status", "committed")
    }

    private fun rollback(ctx: Context) {
        coordinator().rollback(txIdHeader(ctx), currentOwnerKey(ctx))
        ctx.jsonField("status", "rolledback")
    }

    /** `open`, `committed`, `rolledback` ou `unknown` — desfaz a dúvida de quem perdeu a resposta do desfecho. */
    private fun status(ctx: Context) {
        ctx.jsonField("status", coordinator().status(txIdHeader(ctx), currentOwnerKey(ctx)))
    }

    /**
     * A chave opaca do dono, em espaços de nome separados para um cliente anônimo não se passar por usuário:
     * autenticado → `user:<id>` (o [CLIENT_HEADER] é ignorado); sem usuário → `anon:<X-Client-Id>`, ou `null`
     * se o cabeçalho não veio.
     */
    internal fun currentOwnerKey(ctx: Context): String? {
        val sc = SecurityContextHolder.get()
        if (sc != null) {
            return "user:${sc.userId}"
        }
        return ctx.header(CLIENT_HEADER)?.takeIf { it.isNotBlank() }?.let { "anon:$it" }
    }

    private fun txIdHeader(ctx: Context): String =
        ctx.header(TX_HEADER)?.takeIf { it.isNotBlank() } ?: throw AccessDeniedException("Cabeçalho $TX_HEADER ausente")

    private fun coordinator(): RemoteTransactionCoordinator =
        RemoteTransactions.COORDINATOR.getOrNull() ?: throw IllegalStateException("Coordenador de transação remota não inicializado")
}
