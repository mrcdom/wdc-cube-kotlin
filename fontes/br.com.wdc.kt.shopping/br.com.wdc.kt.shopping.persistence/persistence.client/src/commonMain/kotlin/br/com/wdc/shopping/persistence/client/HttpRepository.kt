package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.commons.serialization.JsonStreamReader
import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.framework.domain.repository.Repository

/**
 * Repositório sobre HTTP, genérico, que lê e grava: as consultas vêm de [HttpReadOnlyRepository], e aqui
 * ficam `insert`, `update` e `delete`.
 *
 * As subclasses fixam o caminho base e acrescentam o que a entidade tem de próprio. `fetchById` vem do
 * default da interface da entidade: é um `fetch` com limite 1.
 *
 * @param basePath caminho da entidade na API (ex.: `/api/repo/product`)
 */
abstract class HttpRepository<E, C, K>(
    transport: HttpTransport,
    codec: ModelCodec<E, C>,
    basePath: String,
) : HttpReadOnlyRepository<E, C, K>(transport, codec, basePath), Repository<E, C, K> {

    /** Envia `{"entity", "projection"?}` e completa [bean] com a entidade que o servidor devolve ([fill]). */
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
     * Copia para [target] o que [stored] traz: cada campo com valor, e a associação que [target] não tinha (ou tinha
     * sem chave). É como o bean de quem chamou fica completo depois do insert.
     */
    protected abstract fun fill(target: E, stored: E)

    /**
     * Envia só os campos da projeção efetiva: a informada; senão, o que mudou em relação a [oldBean]; senão, os
     * campos com valor (comparados com a entidade vazia), como no `insert` — um nulo não limpa a coluna.
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
