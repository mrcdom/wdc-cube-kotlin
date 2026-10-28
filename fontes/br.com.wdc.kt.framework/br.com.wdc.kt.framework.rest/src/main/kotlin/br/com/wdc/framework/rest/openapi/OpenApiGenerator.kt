package br.com.wdc.framework.rest.openapi

import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.RestRoute
import com.google.gson.GsonBuilder
import java.lang.reflect.Type

/**
 * Monta o documento OpenAPI a partir das rotas registradas. Uma rota sem [OperationDoc] também entra, com o
 * verbo, o caminho, os parâmetros de caminho e a segurança — o documento nunca deixa de listar uma rota servida.
 *
 * A segurança de cada operação sai do registro: rota pública (lista de `permitAll` ou `public = true`) leva
 * `security: []`; as outras herdam a do documento.
 */
internal class OpenApiGenerator(private val api: RestApi, private val config: OpenApiConfig) {

    private val schemas = SchemaGenerator(api.json.options.fieldNaming)

    fun document(): Map<String, Any?> {
        val paths = LinkedHashMap<String, MutableMap<String, Any?>>()
        for (route in api.routes) {
            if (route.hidden) continue
            paths.getOrPut(openApiPath(route.path)) { LinkedHashMap() }[route.method.lowercase()] = operation(route)
        }

        val tags = LinkedHashMap<String, String?>(config.tags)
        api.routes.flatMap { it.doc?.tags ?: emptyList() }.forEach { tags.putIfAbsent(it, null) }

        val components = LinkedHashMap<String, Any?>()
        val allSchemas = LinkedHashMap<String, Any?>(config.schemas)
        schemas.components.forEach { (name, schema) -> allSchemas.putIfAbsent(name, schema) }
        if (allSchemas.isNotEmpty()) components["schemas"] = allSchemas
        if (config.parameters.isNotEmpty()) components["parameters"] = config.parameters
        config.securityScheme?.let { (name, scheme) -> components["securitySchemes"] = mapOf(name to scheme) }

        val info = linkedMapOf<String, Any?>("title" to config.title, "version" to config.version)
        config.description?.let { info["description"] = it }
        info.putAll(config.info)

        val document = linkedMapOf<String, Any?>("openapi" to config.openapiVersion, "info" to info)
        if (config.servers.isNotEmpty()) document["servers"] = config.servers.map { mapOf("url" to it) }
        if (tags.isNotEmpty()) {
            document["tags"] = tags.map { (name, description) -> linkedMapOf<String, Any?>("name" to name).apply { description?.let { put("description", it) } } }
        }
        if (api.security != null) {
            config.securityScheme?.let { (name, _) -> document["security"] = listOf(mapOf(name to emptyList<String>())) }
        }
        document["paths"] = paths
        if (components.isNotEmpty()) document["components"] = components
        document.putAll(config.raw)
        return document
    }

    private fun operation(route: RestRoute): Map<String, Any?> {
        val doc = route.doc ?: OperationDoc()
        val op = LinkedHashMap<String, Any?>()
        if (doc.tags.isNotEmpty()) op["tags"] = doc.tags.distinct()
        op["summary"] = doc.summary ?: "${route.method} ${route.path}"
        doc.description?.let { op["description"] = it }
        doc.operationId?.let { op["operationId"] = it }
        if (doc.deprecated) op["deprecated"] = true

        val parameters = ArrayList<Any?>()
        val declared = doc.parameters.filterIsInstance<ParameterDoc>()
        for (name in pathParameters(route.path)) {
            val explicit = declared.firstOrNull { it.location == "path" && it.name == name }
            parameters += parameter(explicit ?: ParameterDoc(name, "path", String::class.java, null, true))
        }
        for (p in doc.parameters) {
            when (p) {
                is ParameterDoc -> if (p.location != "path") parameters += parameter(p)
                else -> parameters += p
            }
        }
        if (parameters.isNotEmpty()) op["parameters"] = parameters

        doc.requestBody?.let { body ->
            op["requestBody"] = mapOf(
                "required" to doc.requestBodyRequired,
                "content" to mapOf(doc.requestContentType to mapOf("schema" to schema(body))),
            )
        }

        val responses = LinkedHashMap<String, Any?>()
        for ((status, response) in doc.responses) {
            responses[status] = when (response) {
                is ResponseDoc -> linkedMapOf<String, Any?>("description" to response.description).apply {
                    response.schema?.let { put("content", mapOf(response.contentType to mapOf("schema" to schema(it)))) }
                }
                else -> response
            }
        }
        if (responses.isEmpty()) responses["200"] = mapOf("description" to "OK")
        op["responses"] = responses

        if (api.security != null && isPublic(route)) op["security"] = emptyList<Any>()
        op.putAll(doc.raw)
        return op
    }

    private fun isPublic(route: RestRoute): Boolean {
        val security = api.security ?: return true
        // o modelo do caminho casa como caminho concreto: `{id}` é um segmento qualquer
        return route.public || security.isPublic(route.method, route.path.replace(Regex("\\{[^}]+}"), "x").replace(Regex("<[^>]+>"), "x"))
    }

    private fun parameter(p: ParameterDoc): Map<String, Any?> = linkedMapOf<String, Any?>(
        "name" to p.name, "in" to p.location, "required" to p.required, "schema" to schemas.schemaOf(p.type),
    ).apply { p.description?.let { put("description", it) } }

    @Suppress("UNCHECKED_CAST")
    private fun schema(value: Any): Any? = when (value) {
        is Map<*, *> -> value
        is Type -> schemas.schemaOf(value)
        is kotlin.reflect.KClass<*> -> schemas.schemaOf(value.java)
        else -> throw IllegalArgumentException("Esquema não reconhecido: $value")
    }

    companion object {
        private val PATH_PARAM = Regex("[{<]([^}>]+)[}>]")

        fun openApiPath(path: String): String = path.replace(PATH_PARAM) { "{${it.groupValues[1]}}" }

        fun pathParameters(path: String): List<String> = PATH_PARAM.findAll(path).map { it.groupValues[1] }.toList()

        fun toJson(document: Map<String, Any?>): String =
            GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(document)
    }
}
