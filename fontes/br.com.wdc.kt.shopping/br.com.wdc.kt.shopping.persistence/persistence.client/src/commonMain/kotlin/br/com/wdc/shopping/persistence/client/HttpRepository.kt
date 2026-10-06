package br.com.wdc.shopping.persistence.client

import br.com.wdc.framework.commons.serialization.InputCoerceUtils
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
}
