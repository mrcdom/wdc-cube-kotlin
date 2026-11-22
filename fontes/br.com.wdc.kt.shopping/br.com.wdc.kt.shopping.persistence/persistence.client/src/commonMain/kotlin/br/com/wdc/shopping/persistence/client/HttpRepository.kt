package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.commons.serialization.JsonStreamReader
import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.framework.domain.repository.Repository

/**
 * Generic read-write repository over HTTP: the queries come from [HttpReadOnlyRepository], and here are
 * `insert`, `update` and `delete`.
 *
 * Subclasses set the base path and add what is specific to the entity. `fetchById` comes from the entity
 * interface's default: a `fetch` with limit 1.
 *
 * @param basePath the entity's path in the API (e.g. `/api/repo/product`)
 */
abstract class HttpRepository<E, C, K>(
    transport: HttpTransport,
    codec: ModelCodec<E, C>,
    basePath: String,
) : HttpReadOnlyRepository<E, C, K>(transport, codec, basePath), Repository<E, C, K> {

    /** Sends `{"entity", "projection"?}` and completes [bean] with the entity the server returns ([fill]). */
    override suspend fun insert(bean: E, projection: E?): Boolean {
        val response = post("insert") { out ->
            out.beginObject()
            out.name("entity")
            codec.writeEntity(out, bean)
            projection?.let {
                out.name("projection")
                codec.writeEntity(out, it)
            }
            out.endObject()
        }
        var success = false
        var id = -1L
        var stored: E? = null
        response.readObject { name ->
            when (name) {
                "success" -> success = InputCoerceUtils.asBoolean(response) == true
                "id" -> InputCoerceUtils.asLong(response)?.let { id = it }
                "entity" -> stored = codec.readEntity(response)
                else -> response.skipValue()
            }
        }
        if (success) {
            val completed = stored
            if (completed != null) fill(bean, completed) else if (id >= 0) codec.setGeneratedId(bean, id)
        }
        return success
    }

    /**
     * Copies into [target] what [stored] carries: each field with a value, and the association [target] lacked (or
     * had without a key). This is how the caller's bean becomes complete after the insert.
     */
    protected abstract fun fill(target: E, stored: E)

    /**
     * Sends only the fields of the effective projection: the given one; otherwise, what changed relative to
     * [oldBean]; otherwise, the fields with a value (compared with the empty entity), as in `insert` — a null
     * does not clear the column.
     */
    override suspend fun update(newBean: E, oldBean: E?, projection: E?): Boolean {
        val effective = projection ?: codec.computeProjection(newBean, oldBean ?: codec.readEntity(JsonStreamReader("{}")))
        val response = post("update") { codec.writeEntityProjected(it, newBean, effective) }
        var success = false
        response.readObject { name ->
            if (name == "success") success = InputCoerceUtils.asBoolean(response) == true else response.skipValue()
        }
        return success
    }

    override suspend fun delete(criteria: C): Int = readCount(postCriteria("delete", criteria))
}
