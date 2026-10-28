package br.com.wdc.framework.rest.openapi

/** O documento OpenAPI: onde é servido e o que não sai das rotas. */
class OpenApiConfig {

    /** Onde o documento é servido (público). */
    var path: String = "/openapi.json"

    var openapiVersion: String = "3.0.3"

    var title: String = "API"
    var version: String = "1.0.0"
    var description: String? = null

    /** `servers`: as URLs base. Vazio = omitido. */
    val servers = ArrayList<String>()

    /** `tags` com descrição, na ordem em que aparecem. */
    val tags = LinkedHashMap<String, String?>()

    /**
     * O esquema de segurança das rotas protegidas, em `components.securitySchemes`. Padrão: Bearer JWT. `null` =
     * sem esquema e sem `security` nas operações.
     */
    var securityScheme: Pair<String, Map<String, Any?>>? = "bearerAuth" to mapOf("type" to "http", "scheme" to "bearer", "bearerFormat" to "JWT")

    /** Esquemas escritos à mão, em `components.schemas` (os gerados por reflexão se somam a eles). */
    val schemas = LinkedHashMap<String, Any?>()

    /** Parâmetros reutilizáveis, em `components.parameters`. */
    val parameters = LinkedHashMap<String, Any?>()

    /** Chaves cruas do topo do documento, aplicadas por último. */
    val raw = LinkedHashMap<String, Any?>()

    /** Chaves cruas de `info`, aplicadas por último. */
    val info = LinkedHashMap<String, Any?>()

    fun tag(name: String, description: String? = null) {
        tags[name] = description
    }
}
