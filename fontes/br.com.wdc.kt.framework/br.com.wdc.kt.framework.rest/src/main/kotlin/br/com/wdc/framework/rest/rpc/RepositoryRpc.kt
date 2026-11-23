package br.com.wdc.framework.rest.rpc

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.commons.serialization.SerializationToken
import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.framework.domain.criteria.CriterionCodec
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.exception.NotFoundException
import br.com.wdc.framework.domain.repository.ReadOnlyRepository
import br.com.wdc.framework.domain.repository.Repository
import br.com.wdc.framework.rest.RawJson
import br.com.wdc.framework.rest.RequestContext
import br.com.wdc.framework.rest.RestApi
import br.com.wdc.framework.rest.RestCall
import br.com.wdc.framework.rest.openapi.OperationDoc
import br.com.wdc.framework.rest.security.AccessRule
import io.javalin.http.NotFoundResponse

/**
 * The generic RPC of an entity: the repository's operations as `POST <base>/<operation>` routes, read and
 * written with the entity's [ModelCodec] — the same one the client uses.
 *
 * | Route | Body | Response |
 * |---|---|---|
 * | `POST insert` | `{"entity", "projection"?}`, or the bare entity | `{"success", "id", "entity"}` (the complete bean) |
 * | `POST update` | fields to update (those present, including `null`) | `{"success"}` |
 * | `POST delete` | criteria | `{"count"}` |
 * | `POST count` | criteria | `{"count"}` |
 * | `POST fetch` | criteria + `projection`, `offset`, `limit` | `{"items"}` (at most [maxLimit]) |
 * | `POST fetch-page` | criteria + `projection`, `page`, `pageSize` | `{"items", "totalItems"}` |
 * | `POST fetch-by-id` | `{"id", "projection"?}` | entity, or 404 |
 * | `GET {id}` | — | entity with the default projection, or 404 |
 *
 * The writes only exist if the repository is a [Repository], and they are `transactional`. The reads run in the
 * client's remote transaction when the request carries its `X-Tx-Id`, so they see what it already wrote. Access is up to the
 * application: [rule] per operation, [scope] to restrict reads, delete and update to what belongs to the caller, and the write
 * hooks, which run inside the transaction. Fetching by key goes through the same rule and reach as the queries.
 *
 * Insert returns the entity as the repository left it (the generated key, the column defaults), passed through
 * [sanitizeOutput] like every entity read. A body without `entity` is read as the bare entity, the form before
 * 0.4.0; so an entity with a field named `entity` needs the envelope.
 *
 * @param K key type
 */
class RepositoryRpc<E : Any, C : Any, K : Any>(
    /** Route prefix: `/api/repo/product`. */
    val basePath: String,
    val codec: ModelCodec<E, C>,
    /** Resolved on each request: the repository may be swapped (tests, restart). */
    val repository: () -> ReadOnlyRepository<E, C, K>,
    /** Empty criteria. */
    val newCriteria: () -> C,
    /** Sets the projection on the criteria. */
    val setProjection: (C, E) -> Unit,
    /** Restricts the criteria to the key. */
    val restrictToId: (C, K) -> Unit,
    /** Converts the key from the path and from the `fetch-by-id` body. */
    val parseId: (String) -> K?,
    /** The entity's key, for the insert response. */
    val idOf: (E) -> K?,
) {
    /**
     * Also registers the writes. Turn off for a read-only repository: the writes require a [Repository]
     * (checked on the request, because the repository is resolved there).
     */
    var writes: Boolean = true

    /** RPC operations, for [rule] and [doc]. */
    enum class Operation(val route: String, val write: Boolean) {
        INSERT("insert", true), UPDATE("update", true), DELETE("delete", true),
        COUNT("count", false), FETCH("fetch", false), FETCH_PAGE("fetch-page", false), FETCH_BY_ID("fetch-by-id", false),

        /** `GET {id}`. */
        GET_BY_ID("{id}", false),
    }

    /** The access rule of each operation, combined with the path's. `null` = only the path's. */
    var rule: (Operation) -> AccessRule? = { null }

    /**
     * Restricts what the caller can reach: the criteria of every read and of delete, and the row an update targets
     * (an update outside it is answered with `NotFoundException`, 404, before [beforeUpdate] — the same as a row that
     * doesn't exist: a 403 would tell the caller the row is there, which a fetch already hides).
     *
     * **The criteria came from the caller**, including each field's disjunction. Add the reach with the conjunctive
     * field — `criteria.ownerId.and().eq(owner)` —, never with just `eq`: on a field the client marked with
     * `"or": true`, `eq` becomes one more alternative (`ownerId IS NOT NULL OR ownerId = :owner`) and the reach is lost.
     * With `and()`, every client condition on that field is `AND`ed with the owner, and none reaches another row.
     */
    var scope: suspend (C, RequestContext) -> Unit = { _, _ -> }
        set(value) {
            field = value
            scoped = true
        }

    /** Whether [scope] was set: without one, an update does not pay for the reach check. */
    private var scoped = false

    /** Adjusts the requested projection before the query — remove what never goes out (the password, for example). */
    var sanitizeProjection: (E) -> Unit = {}

    /** Last barrier before the response: clears what must not go out from each entity read. */
    var sanitizeOutput: (E) -> Unit = {}

    /** Before insert, inside the transaction: validate, complete, refuse with `AccessDeniedException`. */
    var beforeInsert: suspend (E, RequestContext) -> Unit = { _, _ -> }

    /** Before update, inside the transaction. */
    var beforeUpdate: suspend (ModelCodec.UpdateData<E>, RequestContext) -> Unit = { _, _ -> }

    /**
     * Most rows a query returns (`fetch`, `fetch-page`); `null` = no cap. A `limit` or `pageSize` above it is refused
     * (400). A `fetch` without `limit` that would bring more rows is refused too, instead of cut short: the client
     * pages it with `limit` and `offset`.
     */
    var maxLimit: Int? = DEFAULT_MAX_LIMIT

    /** Documentation of each operation; the default describes the contract above. */
    var doc: (Operation) -> (OperationDoc.() -> Unit)? = { null }

    /** The id in the insert response. Default: a number if it is a number, text otherwise, `-1` if there is none. */
    var writeId: (ExtensibleObjectOutput, K?) -> Unit = { out, id ->
        when (id) {
            null -> out.value(-1L)
            is Number -> out.value(id.toLong())
            else -> out.value(id.toString())
        }
    }

    companion object {
        /** The default of [maxLimit]. */
        const val DEFAULT_MAX_LIMIT = 1000
    }

    /** Registers the routes on [routes]. */
    fun register(routes: RestApi.Routes) {
        if (writes) {
            post(routes, Operation.INSERT, transactional = true, ::insert)
            post(routes, Operation.UPDATE, transactional = true, ::update)
            post(routes, Operation.DELETE, transactional = true, ::delete)
        }
        // reads are not transactional, but with X-Tx-Id they run in the client's remote transaction (its own writes)
        post(routes, Operation.COUNT, transactional = false) { call -> call.inRemoteTransaction { count(call) } }
        post(routes, Operation.FETCH, transactional = false) { call -> call.inRemoteTransaction { fetch(call) } }
        post(routes, Operation.FETCH_PAGE, transactional = false) { call -> call.inRemoteTransaction { fetchPage(call) } }
        post(routes, Operation.FETCH_BY_ID, transactional = false) { call -> call.inRemoteTransaction { fetchByIdPost(call) } }
        routes.get("$basePath/{id}", rule = rule(Operation.GET_BY_ID), doc = doc(Operation.GET_BY_ID)) { call ->
            call.inRemoteTransaction { fetchById(call) }
        }
    }

    private fun post(routes: RestApi.Routes, op: Operation, transactional: Boolean, handler: suspend (RestCall) -> Any?) {
        routes.post("$basePath/${op.route}", rule = rule(op), transactional = transactional, doc = doc(op), handler = handler)
    }

    @Suppress("UNCHECKED_CAST")
    private fun writable(): Repository<E, C, K> = repository() as? Repository<E, C, K>
        ?: throw IllegalStateException("The repository of $basePath is read-only: register the RPC with writes = false")

    private suspend fun insert(call: RestCall): RawJson {
        val (entity, projection) = readInsert(call)
        beforeInsert(entity, call.request)
        val success = writable().insert(entity, projection)
        val id = idOf(entity)
        if (success) sanitizeOutput(entity)
        return RawJson.write { out ->
            out.beginObject().name("success").value(success).name("id")
            writeId(out, id)
            if (success) {
                out.name("entity")
                codec.writeEntity(out, entity)
            }
            out.endObject()
        }
    }

    /** `{"entity", "projection"?}`; without `entity`, the whole body is the entity. */
    private fun readInsert(call: RestCall): Pair<E, E?> {
        val envelope = call.readingBody {
            val input = call.bodyInput()
            var found = false
            input.beginObject()
            while (input.hasNext()) {
                if (input.nextName() == "entity") found = true
                input.skipValue()
            }
            input.endObject()
            found
        }
        if (!envelope) return call.entity(codec) to null
        var entity: E? = null
        var projection: E? = null
        call.readingBody {
            val input = call.bodyInput()
            input.beginObject()
            while (input.hasNext()) {
                when (input.nextName()) {
                    "entity" -> entity = codec.readEntity(input)
                    "projection" -> projection = if (input.peek() == SerializationToken.NULL) input.nextNull() else codec.readEntity(input)
                    else -> input.skipValue()
                }
            }
            input.endObject()
        }
        return (entity ?: throw InvalidRequestException("insert without the entity")) to projection
    }

    private suspend fun update(call: RestCall): RawJson {
        val data = call.entityForUpdate(codec)
        if (scoped) {
            val id = idOf(data.entity) ?: throw InvalidRequestException("update without the id")
            val target = newCriteria()
            restrictToId(target, id)
            scope(target, call.request)
            if (repository().count(target) == 0) throw NotFoundException("Not found")
        }
        beforeUpdate(data, call.request)
        return RawJson.field("success", writable().update(data.entity, null, data.projection))
    }

    private suspend fun delete(call: RestCall): RawJson {
        val criteria = readFetchRequest(call).criteria
        scope(criteria, call.request)
        return RawJson.field("count", writable().delete(criteria).toLong())
    }

    private suspend fun count(call: RestCall): RawJson {
        val criteria = readFetchRequest(call).criteria
        scope(criteria, call.request)
        return RawJson.field("count", repository().count(criteria).toLong())
    }

    private suspend fun fetch(call: RestCall): RawJson {
        val request = readFetchRequest(call)
        val cap = maxLimit
        val unlimited = request.limit <= 0
        if (cap != null && request.limit > cap) throw InvalidRequestException("limit above the maximum of $cap rows")
        scope(request.criteria, call.request)
        // without a limit, one row past the cap tells "exactly the cap" from "more than it"
        val limit = if (cap != null && unlimited) cap + 1 else request.limit
        val items = repository().fetch(request.criteria, request.offset, limit)
        if (cap != null && unlimited && items.size > cap) {
            throw InvalidRequestException("More than $cap rows: page the query with limit and offset")
        }
        items.forEach(sanitizeOutput)
        return RawJson.write { writeItems(it, items, null) }
    }

    private suspend fun fetchPage(call: RestCall): RawJson {
        val request = readFetchRequest(call)
        val cap = maxLimit
        if (cap != null && request.pageSize !in 1..cap) throw InvalidRequestException("pageSize must be between 1 and $cap")
        scope(request.criteria, call.request)
        val page = repository().fetchPage(request.criteria, request.page, request.pageSize)
        page.items.forEach(sanitizeOutput)
        return RawJson.write { writeItems(it, page.items, page.totalItems) }
    }

    private suspend fun fetchById(call: RestCall): RawJson {
        val id = parseId(call.path("id")) ?: throw InvalidRequestException("invalid id")
        return respondEntity(fetchOne(call, id, null))
    }

    private suspend fun fetchByIdPost(call: RestCall): RawJson {
        var id: K? = null
        var projection: E? = null
        call.readingBody {
            val input = call.bodyInput()
            input.beginObject()
            while (input.hasNext()) {
                when (input.nextName()) {
                    "id" -> {
                        val text = if (input.peek() == SerializationToken.NUMBER) InputCoerceUtils.asLong(input)?.toString() else InputCoerceUtils.asString(input)
                        id = text?.let(parseId)
                    }
                    "projection" -> projection = codec.readEntity(input)
                    else -> input.skipValue()
                }
            }
            input.endObject()
        }
        return respondEntity(fetchOne(call, id ?: throw InvalidRequestException("fetch-by-id requires the id"), projection))
    }

    private suspend fun fetchOne(call: RestCall, id: K, projection: E?): E? {
        val criteria = newCriteria()
        restrictToId(criteria, id)
        val shape = projection ?: repository().ownFieldsProjection()
        sanitizeProjection(shape)
        setProjection(criteria, shape)
        scope(criteria, call.request)
        return repository().fetch(criteria, 0, 1).firstOrNull()
    }

    private fun respondEntity(entity: E?): RawJson {
        if (entity == null) throw NotFoundResponse("Not found")
        sanitizeOutput(entity)
        return RawJson.write { codec.writeEntity(it, entity) }
    }

    /** The criteria, the projection (without it, the repository's default) and the slice of a query request. */
    private class FetchRequest<C>(val criteria: C) {
        var offset = 0
        var limit = 0
        var page = 0
        var pageSize = 0
    }

    /**
     * Reads `{ <criteria fields>, "projection": {…}?, "offset"?, "limit"?, "page"?, "pageSize"? }`. A name that
     * is neither a criteria field nor a slice field is ignored.
     */
    private fun readFetchRequest(call: RestCall): FetchRequest<C> {
        val request = FetchRequest(newCriteria())
        var projection: E? = null
        call.readingBody {
            val input: ExtensibleObjectInput = call.bodyInput()
            input.beginObject()
            while (input.hasNext()) {
                when (val name = input.nextName()) {
                    "projection" -> projection = codec.readEntity(input)
                    "offset" -> request.offset = CriterionCodec.readSliceNumber(input, "offset") ?: 0
                    "limit" -> request.limit = CriterionCodec.readSliceNumber(input, "limit") ?: 0
                    "page" -> request.page = CriterionCodec.readSliceNumber(input, "page") ?: 0
                    "pageSize" -> request.pageSize = CriterionCodec.readSliceNumber(input, "pageSize") ?: 0
                    else -> if (!codec.readCriteriaField(input, name, request.criteria)) input.skipValue()
                }
            }
            input.endObject()
        }
        // outside the body reading: a failure here is the server's, not the request's
        val shape = projection ?: repository().ownFieldsProjection()
        sanitizeProjection(shape)
        setProjection(request.criteria, shape)
        return request
    }

    /** `{ "items": [ … ] }`, with `totalItems` when given. */
    private fun writeItems(out: ExtensibleObjectOutput, items: List<E>, totalItems: Int?) {
        out.beginObject()
        out.name("items").beginArray()
        items.forEach { codec.writeEntity(out, it) }
        out.endArray()
        totalItems?.let { out.name("totalItems").value(it.toLong()) }
        out.endObject()
    }
}
