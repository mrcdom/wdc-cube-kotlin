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

    /**
     * Insere [bean]. A [projection] diz quais campos entram no INSERT (campo não nulo nela); o que fica fora recebe o
     * padrão da coluna. Sem projeção, entram os campos que têm valor no bean (o nulo fica fora e também recebe o
     * padrão). Volta `true` se a linha entrou, e o próprio [bean] sai completo: a chave gerada e o que o banco
     * preencheu (padrões, colunas calculadas).
     *
     * O mesmo vale para o [update], que já recebe a projeção. A diferença é que, no insert, ficar fora significa
     * "padrão da coluna", e não "não muda".
     */
    suspend fun insert(bean: E, projection: E? = null): Boolean

    /**
     * Atualiza uma entidade.
     *
     * @param newBean    valores novos
     * @param oldBean    valores antigos, base da comparação (pode ser `null`)
     * @param projection indica quais campos considerar — campo não-nulo na projeção = "atualizar este campo";
     *                   `null` usa [ownFieldsProjection]
     */
    suspend fun update(newBean: E, oldBean: E? = null, projection: E? = null): Boolean

    /** Insere se [oldBean] for `null`; atualiza caso contrário. A [projection] vale para os dois. */
    suspend fun insertOrUpdate(newBean: E, oldBean: E?, projection: E? = null): Boolean =
        if (oldBean == null) insert(newBean, projection) else update(newBean, oldBean, projection)

    suspend fun delete(criteria: C): Int
}
