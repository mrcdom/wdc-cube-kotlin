package br.com.wdc.framework.rest

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.JsonStreamWriter

/**
 * Resposta com status e cabeçalhos — o `ResponseEntity`. O corpo segue as mesmas regras do valor devolvido
 * pelo handler (ver [RestCall]); `null` = corpo vazio.
 */
class RestResponse(
    val status: Int,
    val body: Any? = null,
    val headers: Map<String, String> = emptyMap(),
    /** Content-Type explícito; `null` = deduzido do corpo. */
    val contentType: String? = null,
) {
    fun withHeader(name: String, value: String) = RestResponse(status, body, headers + (name to value), contentType)

    companion object {
        fun ok(body: Any? = null) = RestResponse(200, body)
        fun created(body: Any? = null) = RestResponse(201, body)
        fun noContent() = RestResponse(204)
        fun status(status: Int, body: Any? = null) = RestResponse(status, body)
    }
}

/**
 * JSON já pronto, escrito com a API de streaming — é como as entidades saem pelos `ModelCodec`, sem reflexão.
 * O handler devolve um destes e a biblioteca não o passa pelo mapeador de DTO.
 */
class RawJson(val json: String) {
    companion object {
        inline fun write(block: (ExtensibleObjectOutput) -> Unit): RawJson {
            val writer = JsonStreamWriter()
            block(writer)
            return RawJson(writer.result())
        }

        /** `{ "<name>": <value> }` */
        fun field(name: String, value: Boolean) = write { it.beginObject().name(name).value(value).endObject() }

        fun field(name: String, value: String) = write { it.beginObject().name(name).value(value).endObject() }

        fun field(name: String, value: Long) = write { it.beginObject().name(name).value(value).endObject() }
    }
}
