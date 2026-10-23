package br.com.wdc.framework.rest.security

import br.com.wdc.framework.rest.RequestContext

/**
 * Transforma a credencial da requisição em [RestPrincipal]. A biblioteca não sabe o que é um token: a aplicação
 * valida (JWT, sessão, chave de API…) e devolve quem chama.
 *
 * - sem credencial → `null`;
 * - credencial recusada (expirada, assinatura inválida…) → [AuthenticationException].
 *
 * Roda em toda rota da API, pública ou não; numa rota pública a credencial recusada é ignorada, a menos que
 * [SecurityConfig.rejectInvalidCredentialsOnPublicRoutes] diga o contrário.
 */
fun interface Authenticator {
    suspend fun authenticate(request: RequestContext): RestPrincipal?
}

/** A credencial veio, mas não vale. A resposta usa o status de requisição sem credencial válida. */
open class AuthenticationException(message: String) : RuntimeException(message)

/** Sem credencial, numa rota que a exige. */
open class AuthenticationRequiredException(message: String) : AuthenticationException(message)

/**
 * Lê `Authorization: Bearer <token>` e entrega o token a [resolve]. `null` de [resolve] = token recusado.
 *
 * @param scheme o esquema esperado no cabeçalho, comparado sem diferenciar maiúsculas
 */
class BearerAuthenticator(
    private val scheme: String = "Bearer",
    private val header: String = "Authorization",
    private val resolve: suspend (token: String) -> RestPrincipal?,
) : Authenticator {

    override suspend fun authenticate(request: RequestContext): RestPrincipal? {
        val value = request.header(header)?.trim()
        if (value.isNullOrEmpty()) {
            return null
        }
        val prefix = "$scheme "
        if (!value.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)) {
            throw AuthenticationException("Missing or invalid $header header")
        }
        val token = value.substring(prefix.length).trim()
        if (token.isEmpty()) {
            throw AuthenticationException("Missing or invalid $header header")
        }
        return resolve(token) ?: throw AuthenticationException("Invalid or expired token")
    }
}
