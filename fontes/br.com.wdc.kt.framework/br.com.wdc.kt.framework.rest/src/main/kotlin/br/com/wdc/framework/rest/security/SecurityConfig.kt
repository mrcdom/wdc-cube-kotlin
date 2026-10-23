package br.com.wdc.framework.rest.security

/**
 * A segurança da API. Sem ela ([br.com.wdc.framework.rest.RestApi.Builder.security] não chamado), toda rota
 * é aberta e o principal é sempre `null`.
 *
 * Ordem em cada rota: filtros `before` → [authenticator] → regra da rota (ou [defaultRule]) → handler.
 */
class SecurityConfig {

    /** Quem transforma a credencial em principal. Obrigatório. */
    var authenticator: Authenticator? = null

    /** A regra das rotas que não declaram a sua e não estão em [permitAll]. Padrão: exige autenticação. */
    var defaultRule: AccessRule = AccessRules.authenticated

    /**
     * Status da resposta a quem não tem credencial válida numa rota que a exige. Padrão 401; o Spring Security
     * sem `AuthenticationEntryPoint` responde 403.
     */
    var unauthenticatedStatus: Int = 401

    /**
     * Requisição anônima a um caminho que nenhuma rota atende (e fora de [permitAll]) recebe a resposta de
     * "sem credencial" em vez de 404/405 — como no Spring Security, em que a autorização vem antes do
     * roteamento. Padrão: `false` (404/405 do Javalin).
     */
    var protectUnmatched: Boolean = false

    /**
     * Numa rota pública, credencial recusada é ignorada (a requisição segue anônima). `true` a recusa com a
     * resposta de "sem credencial".
     */
    var rejectInvalidCredentialsOnPublicRoutes: Boolean = false

    internal val publicMatchers = ArrayList<RequestMatcher>()

    /** Rotas públicas, por padrão de caminho com verbo opcional: `"POST /auth/login"`, `"GET /catalog/featured"` (ver [RequestMatcher]). */
    fun permitAll(vararg specs: String) {
        specs.mapTo(publicMatchers) { RequestMatcher.of(it) }
    }

    internal fun isPublic(method: String, path: String): Boolean = publicMatchers.any { it.matches(method, path) }
}
