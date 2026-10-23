package br.com.wdc.framework.rest

/**
 * CORS, pelo plugin do Javalin. A pré-requisição (`OPTIONS`) é respondida pelo plugin, antes das rotas e da
 * segurança. Os métodos e cabeçalhos pedidos pelo navegador são aceitos (o `allowedMethods("*")` do Spring).
 */
class CorsOptions {
    internal val origins = ArrayList<String>()
    internal var anyOrigin = false

    /** `Access-Control-Allow-Credentials`. Não combina com qualquer origem: use [reflectClientOrigin]. */
    var allowCredentials: Boolean = false

    /** Devolve a origem do pedido como permitida — "qualquer origem" com credenciais. */
    var reflectClientOrigin: Boolean = false

    /** `Access-Control-Max-Age`, em segundos; negativo = não envia. */
    var maxAge: Int = -1

    /** Caminhos a que a regra se aplica, no formato do Javalin. */
    var path: String = "*"

    val exposedHeaders = ArrayList<String>()

    fun anyOrigin() {
        anyOrigin = true
    }

    fun allowOrigins(vararg origins: String) {
        this.origins += origins
    }
}

/** Compressão das respostas. */
class CompressionOptions {
    /** Nível do gzip, de 1 a 9. */
    var gzipLevel: Int = 6

    /** Respostas menores que isto saem sem compressão. O padrão é o do Spring Boot (2 KB). */
    var minSize: Int = 2048

    /** Só estes tipos são comprimidos; vazio = os do Javalin (todos, salvo imagens, vídeo e compactados). */
    val mimeTypes = ArrayList<String>()

    companion object {
        /** Os tipos que o Spring Boot comprime por padrão. */
        val SPRING_MIME_TYPES = listOf(
            "text/html", "text/xml", "text/plain", "text/css", "text/javascript", "application/javascript",
            "application/json", "application/xml",
        )
    }
}
