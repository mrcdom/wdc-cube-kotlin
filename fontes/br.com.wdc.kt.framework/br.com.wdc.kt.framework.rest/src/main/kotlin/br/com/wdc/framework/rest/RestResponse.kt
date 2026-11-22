package br.com.wdc.framework.rest

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.JsonStreamWriter

/**
 * Response with status and headers — the `ResponseEntity`. The body follows the same rules as the value returned
 * by the handler (see [RestCall]); `null` = empty body.
 */
class RestResponse(
    val status: Int,
    val body: Any? = null,
    val headers: Map<String, String> = emptyMap(),
    /** Explicit Content-Type; `null` = inferred from the body. */
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
 * Ready-made JSON, written with the streaming API — this is how entities go out through the `ModelCodec`s, without
 * reflection. The handler returns one of these and the library doesn't pass it through the DTO mapper.
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
