package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.rest.RequestContext
import br.com.wdc.framework.rest.rpc.RepositoryRpc
import br.com.wdc.framework.rest.security.AccessRule
import br.com.wdc.framework.rest.security.AccessRules
import br.com.wdc.framework.rest.security.RestPrincipal
import br.com.wdc.shopping.domain.security.SecurityContext

/** Quem chama a API, autenticado: o [SecurityContext] que o token resolve. */
class ShoppingPrincipal(val context: SecurityContext) : RestPrincipal {
    override val id: String = context.userId.toString()
    override val roles: Set<String> get() = context.permissions
}

/**
 * O controle de acesso da API REST: a permissão de quem chama e o alcance dos dados dele.
 *
 * **É aqui, na fronteira HTTP, que o acesso é conferido** — e só aqui. Os repositórios não conferem nada: quem
 * os chama de dentro do servidor é a camada de apresentação, que o usuário não tem como adulterar; quem os
 * chama de fora passa obrigatoriamente por estas rotas.
 *
 * Com a segurança desligada (sem serviço de autenticação registrado), a API sobe sem segurança: as regras não
 * são consultadas e não há principal.
 */
internal object ApiSecurity {

    /** Exige que quem chama possa executar [operation] em [entity]. */
    fun permission(entity: String, operation: String): AccessRule =
        AccessRules.principalOf<ShoppingPrincipal> { it.context.hasPermission(entity, operation) }

    /** A permissão que cada operação do RPC exige: as escritas `write`, o delete `delete`, as consultas `read`. */
    fun permission(entity: String, operation: RepositoryRpc.Operation): AccessRule = permission(
        entity,
        when (operation) {
            RepositoryRpc.Operation.INSERT, RepositoryRpc.Operation.UPDATE -> "write"
            RepositoryRpc.Operation.DELETE -> "delete"
            else -> "read"
        },
    )

    /** O contexto de quem chama, ou `null` com a segurança desligada. */
    fun context(request: RequestContext): SecurityContext? = request.principalAs<ShoppingPrincipal>()?.context

    /**
     * O usuário a que os dados de quem chama estão restritos, ou `null` se ele alcança os de todos (`data:all`,
     * ou segurança desligada).
     */
    fun ownerScope(request: RequestContext): Long? = context(request)?.takeIf { !it.hasDataAll() }?.userId
}
