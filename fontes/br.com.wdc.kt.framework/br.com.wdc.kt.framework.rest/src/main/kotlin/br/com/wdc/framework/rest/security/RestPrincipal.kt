package br.com.wdc.framework.rest.security

/**
 * Quem chama, autenticado. A aplicação implementa com o que o seu token carrega (usuário, empresa, chaves…) e
 * lê de volta com `RequestContext.principalAs<SeuTipo>()`.
 */
interface RestPrincipal {

    /** Identificador estável de quem chama. É o que distingue o dono de uma transação remota. */
    val id: String

    /** Os papéis, como a aplicação os escreve (`ADMIN`, `ROLE_ADMIN`…): a biblioteca só compara strings. */
    val roles: Set<String>

    fun hasRole(role: String): Boolean = role in roles

    fun hasAnyRole(vararg roles: String): Boolean = roles.any { it in this.roles }
}

/** Principal simples, para quem não precisa de mais que o id e os papéis. */
data class SimplePrincipal(override val id: String, override val roles: Set<String> = emptySet()) : RestPrincipal
