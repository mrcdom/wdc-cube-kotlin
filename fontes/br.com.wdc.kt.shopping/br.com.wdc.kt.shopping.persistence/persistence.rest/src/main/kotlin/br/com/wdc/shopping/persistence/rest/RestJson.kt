package br.com.wdc.shopping.persistence.rest

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.commons.serialization.JsonStreamReader
import br.com.wdc.framework.commons.serialization.JsonStreamWriter
import br.com.wdc.framework.domain.codec.ModelCodec
import io.javalin.http.Context

/** Corpo da requisição como leitor de streaming — os controllers leem com os codecs do domínio, sem reflexão. */
internal fun Context.jsonBody(): ExtensibleObjectInput = JsonStreamReader(body().ifBlank { "{}" })

/** Escreve a resposta JSON com um escritor de streaming. */
internal inline fun Context.jsonResult(write: (ExtensibleObjectOutput) -> Unit) {
    val writer = JsonStreamWriter()
    write(writer)
    contentType("application/json")
    result(writer.result())
}

/** `{ "<name>": <value> }` */
internal fun Context.jsonField(name: String, value: Boolean) = jsonResult { it.beginObject().name(name).value(value).endObject() }

internal fun Context.jsonField(name: String, value: String) = jsonResult { it.beginObject().name(name).value(value).endObject() }

internal fun Context.jsonField(name: String, value: Int) = jsonResult { it.beginObject().name(name).value(value.toLong()).endObject() }

/**
 * O que um pedido de consulta carrega além dos campos do critério: a projeção (já posta no critério por
 * quem lê) e o recorte, por deslocamento ou por página.
 */
internal class FetchRequest<C>(val criteria: C) {
    var offset = 0
    var limit = 0
    var page = 0
    var pageSize = 0
}

/**
 * Lê `{ <campos do critério>, "projection": {…}?, "offset"?, "limit"?, "page"?, "pageSize"? }`.
 * Nome que não é campo do critério nem do recorte é ignorado.
 *
 * @param setProjection põe no critério a projeção lida
 */
internal fun <E, C> ModelCodec<E, C>.readFetchRequest(
    input: ExtensibleObjectInput,
    criteria: C,
    setProjection: (C, E) -> Unit,
): FetchRequest<C> {
    val request = FetchRequest(criteria)
    input.beginObject()
    while (input.hasNext()) {
        when (val name = input.nextName()) {
            "projection" -> setProjection(criteria, readEntity(input))
            "offset" -> request.offset = InputCoerceUtils.asInteger(input, 0) ?: 0
            "limit" -> request.limit = InputCoerceUtils.asInteger(input, 0) ?: 0
            "page" -> request.page = InputCoerceUtils.asInteger(input, 0) ?: 0
            "pageSize" -> request.pageSize = InputCoerceUtils.asInteger(input, 0) ?: 0
            else -> if (!readCriteriaField(input, name, criteria)) input.skipValue()
        }
    }
    input.endObject()
    return request
}

/** `{ "items": [ … ] }`, com `totalItems` quando informado. */
internal fun <E, C> ModelCodec<E, C>.writeItems(out: ExtensibleObjectOutput, items: List<E>, totalItems: Int? = null) {
    out.beginObject()
    out.name("items").beginArray()
    items.forEach { writeEntity(out, it) }
    out.endArray()
    totalItems?.let { out.name("totalItems").value(it.toLong()) }
    out.endObject()
}
