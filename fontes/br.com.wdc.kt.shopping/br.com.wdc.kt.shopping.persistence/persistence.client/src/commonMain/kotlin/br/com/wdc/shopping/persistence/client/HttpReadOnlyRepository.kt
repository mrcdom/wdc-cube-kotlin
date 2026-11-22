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
 * Generic **read-only** repository over HTTP: `count`, `fetch` and `fetch-page` have the same shape for
 * every entity, and what differs — how the entity and the criteria travel — is in the [ModelCodec], the same
 * one the server uses.
 *
 * It is the base of a repository that only queries (a dashboard view, for example): the subclass sets the base
 * path and `fetchById`. [HttpRepository] adds the write operations.
 *
 * @param basePath the entity's path in the API (e.g. `/api/repo/product`)
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

    // :: Support

    protected inline fun post(operation: String, write: (ExtensibleObjectOutput) -> Unit): ExtensibleObjectInput {
        val writer = JsonStreamWriter()
        write(writer)
        return JsonStreamReader(transport.postJson("$basePath/$operation", writer.result()))
    }

    /**
     * Sends `{ <criteria fields>, "projection": {…}?, <extra> }`. The projection is written as an entity, and
     * that is how a projection collection carries its criteria and its subset to the server.
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
