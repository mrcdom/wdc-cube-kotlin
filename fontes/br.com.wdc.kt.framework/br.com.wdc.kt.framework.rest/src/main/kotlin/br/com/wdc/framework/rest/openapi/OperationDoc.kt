package br.com.wdc.framework.rest.openapi

import java.lang.reflect.Type
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

/**
 * A documentação OpenAPI de uma rota. O que pode ser lido da rota não se escreve aqui: verbo, caminho,
 * parâmetros de caminho e a exigência de credencial saem do registro.
 *
 * Um "esquema" aceita: um tipo Kotlin/Java (vira `$ref` a um esquema gerado por reflexão, ver [SchemaGenerator]),
 * um `Map` cru (vai como está) ou `null` (sem corpo).
 */
class OperationDoc {
    var summary: String? = null
    var description: String? = null
    var operationId: String? = null
    var deprecated: Boolean = false
    val tags = ArrayList<String>()

    internal val parameters = ArrayList<Any>()
    internal var requestBody: Any? = null
    internal var requestBodyRequired: Boolean = true
    internal var requestContentType: String = "application/json"
    internal val responses = LinkedHashMap<String, Any>()
    internal val raw = LinkedHashMap<String, Any?>()

    fun tags(vararg names: String) {
        tags += names
    }

    /** Parâmetro de query. */
    fun query(name: String, type: Type = String::class.java, description: String? = null, required: Boolean = false) {
        parameters += ParameterDoc(name, "query", type, description, required)
    }

    /** Parâmetro de cabeçalho. */
    fun header(name: String, type: Type = String::class.java, description: String? = null, required: Boolean = false) {
        parameters += ParameterDoc(name, "header", type, description, required)
    }

    /** Tipo e descrição de um parâmetro de caminho (sem isto, sai como `string`). */
    fun path(name: String, type: Type = String::class.java, description: String? = null) {
        parameters += ParameterDoc(name, "path", type, description, true)
    }

    /** Parâmetro já escrito em OpenAPI (inclusive `$ref`). */
    fun parameter(raw: Map<String, Any?>) {
        parameters += raw
    }

    fun body(schema: Any?, required: Boolean = true, contentType: String = "application/json") {
        requestBody = schema
        requestBodyRequired = required
        requestContentType = contentType
    }

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T> accepts(required: Boolean = true) = body(typeOf<T>().javaType, required)

    /** Resposta de [status] com corpo [schema] (`null` = sem corpo). */
    fun response(status: Int, schema: Any? = null, description: String = defaultDescription(status), contentType: String = "application/json") {
        responses[status.toString()] = ResponseDoc(description, schema, contentType)
    }

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T> returns(status: Int = 200, description: String = defaultDescription(status)) =
        response(status, typeOf<T>().javaType, description)

    /** Resposta já escrita em OpenAPI. */
    fun response(status: String, raw: Map<String, Any?>) {
        responses[status] = raw
    }

    /**
     * Chaves cruas da operação OpenAPI, aplicadas por último: vencem o que foi gerado. É a saída para o que o
     * modelo não cobre — e para quem já tem a operação escrita à mão.
     */
    fun raw(values: Map<String, Any?>) {
        raw.putAll(values)
    }

    companion object {
        fun defaultDescription(status: Int): String = when (status) {
            200 -> "OK"
            201 -> "Created"
            204 -> "No Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            409 -> "Conflict"
            else -> "Status $status"
        }
    }
}

internal class ParameterDoc(val name: String, val location: String, val type: Type, val description: String?, val required: Boolean)

internal class ResponseDoc(val description: String, val schema: Any?, val contentType: String)
