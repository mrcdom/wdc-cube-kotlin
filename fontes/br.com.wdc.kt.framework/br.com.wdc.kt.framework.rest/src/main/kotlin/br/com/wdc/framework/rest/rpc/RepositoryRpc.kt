package br.com.wdc.framework.rest.rpc

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.commons.serialization.SerializationToken
import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.framework.domain.exception.InvalidRequestException
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
 * O RPC genérico de uma entidade: as operações do repositório como rotas `POST <base>/<operação>`, lidas e
 * escritas com o [ModelCodec] da entidade — o mesmo que o cliente usa.
 *
 * | Rota | Corpo | Resposta |
 * |---|---|---|
 * | `POST insert` | entidade | `{"success", "id"}` |
 * | `POST update` | campos a atualizar (os presentes, inclusive `null`) | `{"success"}` |
 * | `POST delete` | critério | `{"count"}` |
 * | `POST count` | critério | `{"count"}` |
 * | `POST fetch` | critério + `projection`, `offset`, `limit` | `{"items"}` |
 * | `POST fetch-page` | critério + `projection`, `page`, `pageSize` | `{"items", "totalItems"}` |
 * | `POST fetch-by-id` | `{"id", "projection"?}` | entidade, ou 404 |
 * | `GET {id}` | — | entidade com a projeção padrão, ou 404 |
 *
 * As escritas só existem se o repositório for um [Repository], e são `transactional`. O acesso é da aplicação:
 * [rule] por operação, [scope] para restringir o critério ao que é de quem chama, e os ganchos de escrita, que
 * rodam dentro da transação. Buscar pela chave passa pela mesma regra e pelo mesmo alcance das consultas.
 *
 * @param K tipo da chave
 */
class RepositoryRpc<E : Any, C : Any, K : Any>(
    /** Prefixo das rotas: `/api/repo/product`. */
    val basePath: String,
    val codec: ModelCodec<E, C>,
    /** Resolvido a cada requisição: o repositório pode ser trocado (testes, reinício). */
    val repository: () -> ReadOnlyRepository<E, C, K>,
    /** Critério vazio. */
    val newCriteria: () -> C,
    /** Põe a projeção no critério. */
    val setProjection: (C, E) -> Unit,
    /** Restringe o critério à chave. */
    val restrictToId: (C, K) -> Unit,
    /** Converte a chave do caminho e do corpo de `fetch-by-id`. */
    val parseId: (String) -> K?,
    /** A chave da entidade, para a resposta do insert. */
    val idOf: (E) -> K?,
) {
    /**
     * Registra também as escritas. Desligue para repositório só de leitura: as escritas exigem um [Repository]
     * (é conferido na requisição, porque o repositório é resolvido nela).
     */
    var writes: Boolean = true

    /** Operações do RPC, para [rule] e [doc]. */
    enum class Operation(val route: String, val write: Boolean) {
        INSERT("insert", true), UPDATE("update", true), DELETE("delete", true),
        COUNT("count", false), FETCH("fetch", false), FETCH_PAGE("fetch-page", false), FETCH_BY_ID("fetch-by-id", false),

        /** `GET {id}`. */
        GET_BY_ID("{id}", false),
    }

    /** A regra de acesso de cada operação, somada à do caminho. `null` = só a do caminho. */
    var rule: (Operation) -> AccessRule? = { null }

    /** Restringe o critério de toda leitura e do delete ao que quem chama alcança. */
    var scope: suspend (C, RequestContext) -> Unit = { _, _ -> }

    /** Ajusta a projeção pedida antes da consulta — retire o que nunca sai (a senha, por exemplo). */
    var sanitizeProjection: (E) -> Unit = {}

    /** Última barreira antes da resposta: limpa o que não pode sair de cada entidade lida. */
    var sanitizeOutput: (E) -> Unit = {}

    /** Antes do insert, dentro da transação: validar, completar, recusar com `AccessDeniedException`. */
    var beforeInsert: suspend (E, RequestContext) -> Unit = { _, _ -> }

    /** Antes do update, dentro da transação. */
    var beforeUpdate: suspend (ModelCodec.UpdateData<E>, RequestContext) -> Unit = { _, _ -> }

    /** Documentação de cada operação; o padrão descreve o contrato acima. */
    var doc: (Operation) -> (OperationDoc.() -> Unit)? = { null }

    /** O id na resposta do insert. Padrão: número se for número, texto senão, `-1` se não houver. */
    var writeId: (ExtensibleObjectOutput, K?) -> Unit = { out, id ->
        when (id) {
            null -> out.value(-1L)
            is Number -> out.value(id.toLong())
            else -> out.value(id.toString())
        }
    }

    /** Registra as rotas em [routes]. */
    fun register(routes: RestApi.Routes) {
        if (writes) {
            post(routes, Operation.INSERT, transactional = true, ::insert)
            post(routes, Operation.UPDATE, transactional = true, ::update)
            post(routes, Operation.DELETE, transactional = true, ::delete)
        }
        post(routes, Operation.COUNT, transactional = false, ::count)
        post(routes, Operation.FETCH, transactional = false, ::fetch)
        post(routes, Operation.FETCH_PAGE, transactional = false, ::fetchPage)
        post(routes, Operation.FETCH_BY_ID, transactional = false, ::fetchByIdPost)
        routes.get("$basePath/{id}", rule = rule(Operation.GET_BY_ID), doc = doc(Operation.GET_BY_ID)) { fetchById(it) }
    }

    private fun post(routes: RestApi.Routes, op: Operation, transactional: Boolean, handler: suspend (RestCall) -> Any?) {
        routes.post("$basePath/${op.route}", rule = rule(op), transactional = transactional, doc = doc(op), handler = handler)
    }

    @Suppress("UNCHECKED_CAST")
    private fun writable(): Repository<E, C, K> = repository() as? Repository<E, C, K>
        ?: throw IllegalStateException("O repositório de $basePath é só de leitura: registre o RPC com writes = false")

    private suspend fun insert(call: RestCall): RawJson {
        val entity = call.entity(codec)
        beforeInsert(entity, call.request)
        val success = writable().insert(entity)
        return RawJson.write { out ->
            out.beginObject().name("success").value(success).name("id")
            writeId(out, idOf(entity))
            out.endObject()
        }
    }

    private suspend fun update(call: RestCall): RawJson {
        val data = call.entityForUpdate(codec)
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
        scope(request.criteria, call.request)
        val items = repository().fetch(request.criteria, request.offset, request.limit)
        items.forEach(sanitizeOutput)
        return RawJson.write { writeItems(it, items, null) }
    }

    private suspend fun fetchPage(call: RestCall): RawJson {
        val request = readFetchRequest(call)
        scope(request.criteria, call.request)
        val page = repository().fetchPage(request.criteria, request.page, request.pageSize)
        page.items.forEach(sanitizeOutput)
        return RawJson.write { writeItems(it, page.items, page.totalItems) }
    }

    private suspend fun fetchById(call: RestCall): RawJson {
        val id = parseId(call.path("id")) ?: throw InvalidRequestException("id inválido")
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
        return respondEntity(fetchOne(call, id ?: throw InvalidRequestException("fetch-by-id exige o id"), projection))
    }

    private suspend fun fetchOne(call: RestCall, id: K, projection: E?): E? {
        val criteria = newCriteria()
        restrictToId(criteria, id)
        val shape = projection ?: repository().newProjection()
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

    /** O critério, a projeção (sem ela, a padrão do repositório) e o recorte de um pedido de consulta. */
    private class FetchRequest<C>(val criteria: C) {
        var offset = 0
        var limit = 0
        var page = 0
        var pageSize = 0
    }

    /**
     * Lê `{ <campos do critério>, "projection": {…}?, "offset"?, "limit"?, "page"?, "pageSize"? }`. Nome que
     * não é campo do critério nem do recorte é ignorado.
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
                    "offset" -> request.offset = InputCoerceUtils.asInteger(input, 0) ?: 0
                    "limit" -> request.limit = InputCoerceUtils.asInteger(input, 0) ?: 0
                    "page" -> request.page = InputCoerceUtils.asInteger(input, 0) ?: 0
                    "pageSize" -> request.pageSize = InputCoerceUtils.asInteger(input, 0) ?: 0
                    else -> if (!codec.readCriteriaField(input, name, request.criteria)) input.skipValue()
                }
            }
            input.endObject()
        }
        // fora da leitura do corpo: falha aqui é do servidor, não do pedido
        val shape = projection ?: repository().newProjection()
        sanitizeProjection(shape)
        setProjection(request.criteria, shape)
        return request
    }

    /** `{ "items": [ … ] }`, com `totalItems` quando informado. */
    private fun writeItems(out: ExtensibleObjectOutput, items: List<E>, totalItems: Int?) {
        out.beginObject()
        out.name("items").beginArray()
        items.forEach { codec.writeEntity(out, it) }
        out.endArray()
        totalItems?.let { out.name("totalItems").value(it.toLong()) }
        out.endObject()
    }
}
