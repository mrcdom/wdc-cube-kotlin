package br.com.wdc.framework.rest

import br.com.wdc.framework.rest.openapi.OperationDoc
import br.com.wdc.framework.rest.security.AccessRule

/** O handler de uma rota. O valor devolvido é a resposta (ver [RestCall]). */
typealias RestHandler = suspend (RestCall) -> Any?

/**
 * Uma rota registrada: verbo, modelo de caminho no formato do Javalin (`/user/{id}`, `/files/<path>`) e o que
 * cerca o handler.
 */
class RestRoute internal constructor(
    val method: String,
    val path: String,
    /** A regra da rota, somada à do caminho (pública ou a padrão). `null` = só a do caminho. */
    val rule: AccessRule?,
    /** A rota é pública mesmo fora da lista de rotas públicas. */
    val public: Boolean,
    /** O handler roda na transação da requisição (ver `RestTransactions`). */
    val transactional: Boolean,
    val doc: OperationDoc?,
    /** Fora da descrição OpenAPI. */
    val hidden: Boolean,
    internal val handler: RestHandler,
) {
    override fun toString(): String = "$method $path"
}
