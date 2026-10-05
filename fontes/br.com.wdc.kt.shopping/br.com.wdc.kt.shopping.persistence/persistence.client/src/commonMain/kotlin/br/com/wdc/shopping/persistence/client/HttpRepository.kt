package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.commons.serialization.JsonStreamReader
import br.com.wdc.framework.commons.serialization.JsonStreamWriter
import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.framework.domain.pagination.Page
import br.com.wdc.framework.domain.repository.Repository

/**
 * Repositório sobre HTTP, genérico: as operações têm a mesma forma em todas as entidades, e o que muda —
 * como a entidade e o critério trafegam — está no [ModelCodec], o mesmo que o servidor usa.
 *
 * As subclasses fixam o caminho base e acrescentam o que a entidade tem de próprio. `fetchById` vem do
 * default da interface da entidade: é um `fetch` com limite 1.
 *
 * @param basePath caminho da entidade na API (ex.: `/api/repo/product`)
 */
abstract class HttpRepository<E, C, K>(
    protected val transport: HttpTransport,
    private val codec: ModelCodec<E, C>,
    protected val basePath: String,
) : Repository<E, C, K> {

    override suspend fun insert(bean: E): Boolean {
        val response = post("insert") { codec.writeEntity(it, bean) }
        var success = false
        var id = -1L
        response.readObject { name ->
            when (name) {
                "success" -> success = InputCoerceUtils.asBoolean(response) == true
                "id" -> InputCoerceUtils.asLong(response)?.let { id = it }
                else -> response.skipValue()
            }
        }
        if (success && id >= 0) {
            codec.setGeneratedId(bean, id)
        }
        return success
    }

    /**
     * Envia só os campos da projeção efetiva: a informada; senão, o que mudou em relação a [oldBean];
     * senão, a padrão da entidade.
     */
    override suspend fun update(newBean: E, oldBean: E?, projection: E?): Boolean {
        val effective = projection ?: oldBean?.let { codec.computeProjection(newBean, it) } ?: newProjection()
        val response = post("update") { codec.writeEntityProjected(it, newBean, effective) }
        var success = false
        response.readObject { name ->
            if (name == "success") success = InputCoerceUtils.asBoolean(response) == true else response.skipValue()
        }
        return success
    }

    override suspend fun delete(criteria: C): Int = readCount(postCriteria("delete", criteria))

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

    private inline fun post(operation: String, write: (ExtensibleObjectOutput) -> Unit): ExtensibleObjectInput {
        val writer = JsonStreamWriter()
        write(writer)
        return JsonStreamReader(transport.postJson("$basePath/$operation", writer.result()))
    }

    /**
     * Envia `{ <campos do critério>, "projection": {…}?, <extra> }`. A projeção vai escrita como entidade, e é
     * assim que uma coleção de projeção leva o seu critério e o seu recorte ao servidor.
     */
    private inline fun postCriteria(
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

    private fun readCount(response: ExtensibleObjectInput): Int {
        var count = 0
        response.readObject { name ->
            if (name == "count") count = InputCoerceUtils.asInteger(response, 0) ?: 0 else response.skipValue()
        }
        return count
    }

    private inline fun ExtensibleObjectInput.readObject(onField: (name: String) -> Unit) {
        beginObject()
        while (hasNext()) {
            onField(nextName())
        }
        endObject()
    }
}
