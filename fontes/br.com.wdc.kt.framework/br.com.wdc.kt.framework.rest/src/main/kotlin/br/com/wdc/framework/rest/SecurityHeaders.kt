package br.com.wdc.framework.rest

import io.javalin.http.Context

/**
 * Cabeçalhos de cache e de segurança em toda resposta da API ([RestApi.Builder.securityHeaders]): sucesso, erro
 * mapeado, recusa da autenticação ou da regra, 404/405 tratados pela biblioteca e a pré-requisição do CORS.
 *
 * ```kotlin
 * securityHeaders {
 *     headers -= "X-XSS-Protection"                     // obsoleto nos navegadores atuais
 *     headers["Strict-Transport-Security"] = "max-age=31536000"
 * }
 * ```
 */
class SecurityHeadersOptions {

    /**
     * Escreve `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`, `Pragma: no-cache` e `Expires: 0` nas
     * respostas cuja rota não pôs o próprio `Cache-Control`. A rota que quer cache escreve o seu (`call.header(…)` ou
     * nos cabeçalhos da [RestResponse]), e a resposta sai sem `Pragma` nem `Expires`.
     */
    var noCache: Boolean = true

    /**
     * Escritos sempre, substituindo o que a rota tiver posto com o mesmo nome. Troque um valor, acrescente ou retire
     * cabeçalhos à vontade. Sem `Strict-Transport-Security` por padrão: atrás de um proxy que termina o TLS, o pedido
     * chega em HTTP.
     */
    val headers: MutableMap<String, String> = linkedMapOf(
        "X-Content-Type-Options" to "nosniff",
        "X-Frame-Options" to "DENY",
        "X-XSS-Protection" to "1; mode=block",
    )

    internal fun writeTo(ctx: Context) {
        if (noCache && ctx.res().getHeader(CACHE_CONTROL) == null) {
            ctx.header(CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate")
            ctx.header("Pragma", "no-cache")
            ctx.header("Expires", "0")
        }
        headers.forEach { (name, value) -> ctx.header(name, value) }
    }

    private companion object {
        const val CACHE_CONTROL = "Cache-Control"
    }
}
