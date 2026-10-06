package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.commons.serialization.JsonStreamReader
import br.com.wdc.framework.commons.serialization.JsonStreamWriter
import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.framework.domain.pagination.Page
import br.com.wdc.framework.domain.repository.ReadOnlyRepository

/**
 * Repositório **de somente leitura** sobre HTTP, genérico: `count`, `fetch` e `fetch-page` têm a mesma forma
 * em todas as entidades, e o que muda — como a entidade e o critério trafegam — está no [ModelCodec], o mesmo
 * que o servidor usa.
 *
 * É a base de um repositório que só consulta (uma visão de painel, por exemplo): a subclasse fixa o caminho
 * base e o `fetchById`. [HttpRepository] acrescenta as operações de escrita.
 *
 * @param basePath caminho da entidade na API (ex.: `/api/repo/product`)
 */
abstract class HttpReadOnlyRepository<E, C, K>(
    protected val transport: HttpTransport,
    protected val codec: ModelCodec<E, C>,
    protected val basePath: String,
) : ReadOnlyRepository<E, C, K> {

    override suspend fun count(criteria: C): Int = readCount(postCriteria("count", criteria))

    override suspend fun fetch(criteria: C, offset: Int, limit: Int): List<E> {
        val response = postCriteria("fetch", criteria) { out ->
            if (offset > 0) out.name("offset").value(offset.toLong())
            if (limit > 0) out.name("limit").value(limit.toLong())
        }
        var items: List<E> = emptyList()
        response.readObject { name ->
            if (name == "items") items = codec.readEntityList(response) else response.skipValue()
        }
        return items
    }

    override suspend fun fetchPage(criteria: C, page: Int, pageSize: Int): Page<E> {
        val response = postCriteria("fetch-page", criteria) { out ->
            if (page > 0) out.name("page").value(page.toLong())
            if (pageSize > 0) out.name("pageSize").value(pageSize.toLong())
        }
        var items: List<E> = emptyList()
        var totalItems = 0
        response.readObject { name ->
            when (name) {
                "items" -> items = codec.readEntityList(response)
                "totalItems" -> totalItems = InputCoerceUtils.asInteger(response, 0) ?: 0
                else -> response.skipValue()
            }
        }
        return Page.of(items, page, pageSize, totalItems)
    }

    // :: Apoio

    protected inline fun post(operation: String, write: (ExtensibleObjectOutput) -> Unit): ExtensibleObjectInput {
        val writer = JsonStreamWriter()
        write(writer)
        return JsonStreamReader(transport.postJson("$basePath/$operation", writer.result()))
    }

    /**
     * Envia `{ <campos do critério>, "projection": {…}?, <extra> }`. A projeção vai escrita como entidade, e é
     * assim que uma coleção de projeção leva o seu critério e o seu recorte ao servidor.
     */
    protected inline fun postCriteria(
        operation: String,
        criteria: C,
        extra: (ExtensibleObjectOutput) -> Unit = {},
    ): ExtensibleObjectInput = post(operation) { out ->
        out.beginObject()
        codec.writeCriteriaFields(out, criteria)
        codec.getProjection(criteria)?.let {
            out.name("projection")
            codec.writeEntity(out, it)
        }
        extra(out)
        out.endObject()
    }

    protected fun readCount(response: ExtensibleObjectInput): Int {
        var count = 0
        response.readObject { name ->
            if (name == "count") count = InputCoerceUtils.asInteger(response, 0) ?: 0 else response.skipValue()
        }
        return count
    }

    protected inline fun ExtensibleObjectInput.readObject(onField: (name: String) -> Unit) {
        beginObject()
        while (hasNext()) {
            onField(nextName())
        }
        endObject()
    }
}
