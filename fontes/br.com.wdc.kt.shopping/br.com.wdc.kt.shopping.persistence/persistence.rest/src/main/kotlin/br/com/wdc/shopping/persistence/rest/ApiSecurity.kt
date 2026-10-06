package br.com.wdc.shopping.persistence.rest

import br.com.wdc.shopping.domain.exception.AccessDeniedException
import br.com.wdc.shopping.domain.security.AuthenticationService
import br.com.wdc.shopping.domain.security.SecurityContext
import br.com.wdc.shopping.domain.security.SecurityContextHolder

/**
 * O controle de acesso da API REST: a permissão de quem chama e o alcance dos dados dele.
 *
 * **É aqui, na fronteira HTTP, que o acesso é conferido** — e só aqui. Os repositórios não conferem nada: quem
 * os chama de dentro do servidor é a camada de apresentação, que o usuário não tem como adulterar; quem os
 * chama de fora passa obrigatoriamente por estes controladores.
 *
 * Com a segurança desligada (sem serviço de autenticação registrado), nada é conferido.
 */
internal object ApiSecurity {

    /**
     * Exige que quem chama possa executar [operation] em [entity].
     *
     * @return o contexto de quem chama, ou `null` com a segurança desligada
     * @throws AccessDeniedException sem autenticação ou sem a permissão
     */
    fun require(entity: String, operation: String): SecurityContext? {
        if (AuthenticationService.BEAN.getOrNull() == null) {
            return null
        }
        val sc = SecurityContextHolder.get() ?: throw AccessDeniedException("Authentication required")
        if (!sc.hasPermission(entity, operation)) {
            throw AccessDeniedException("Requires $entity:$operation")
        }
        return sc
    }

    /**
     * O usuário a que os dados de quem chama estão restritos, ou `null` se ele alcança os de todos (`data:all`,
     * ou segurança desligada).
     */
    fun ownerScope(sc: SecurityContext?): Long? = if (sc != null && !sc.hasDataAll()) sc.userId else null
}
