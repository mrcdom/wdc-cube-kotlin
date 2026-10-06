package br.com.wdc.framework.domain.repository

/**
 * Contrato dos repositórios que leem **e gravam**: as consultas de [ReadOnlyRepository] mais inserir,
 * atualizar e apagar.
 *
 * Vale igual para a implementação que fala com o banco (servidor) e para a que fala HTTP (cliente):
 * é o que permite o mesmo caso de uso rodar dos dois lados.
 *
 * @param E tipo da entidade
 * @param C tipo do critério de pesquisa
 * @param K tipo da chave primária
 */
interface Repository<E, C, K> : ReadOnlyRepository<E, C, K> {

    suspend fun insert(bean: E): Boolean

    /**
     * Atualiza uma entidade.
     *
     * @param newBean    valores novos
     * @param oldBean    valores antigos, base da comparação (pode ser `null`)
     * @param projection indica quais campos considerar — campo não-nulo na projeção = "atualizar este campo";
     *                   `null` usa [newProjection]
     */
    suspend fun update(newBean: E, oldBean: E? = null, projection: E? = null): Boolean

    /** Insere se [oldBean] for `null`; atualiza caso contrário. */
    suspend fun insertOrUpdate(newBean: E, oldBean: E?): Boolean =
        if (oldBean == null) insert(newBean) else update(newBean, oldBean)

    suspend fun delete(criteria: C): Int
}
