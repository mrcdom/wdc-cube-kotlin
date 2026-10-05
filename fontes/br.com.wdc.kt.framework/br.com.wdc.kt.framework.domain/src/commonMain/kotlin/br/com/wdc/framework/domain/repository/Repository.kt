package br.com.wdc.framework.domain.repository

import br.com.wdc.framework.domain.pagination.Page

/**
 * Contrato base dos repositórios.
 *
 * Vale igual para a implementação que fala com o banco (servidor) e para a que fala HTTP (cliente):
 * é o que permite o mesmo caso de uso rodar dos dois lados.
 *
 * @param E tipo da entidade
 * @param C tipo do critério de pesquisa
 * @param K tipo da chave primária
 */
interface Repository<E, C, K> {

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

    suspend fun count(criteria: C): Int

    /**
     * Busca pelo critério. `0` em [offset] ou [limit] significa "sem salto" / "sem limite".
     *
     * Atenção à ordem posicional: `fetch(c, 10)` pula 10 linhas. Para limitar, nomeie: `fetch(c, limit = 10)`.
     */
    suspend fun fetch(criteria: C, offset: Int = 0, limit: Int = 0): List<E>

    suspend fun fetchPage(criteria: C, page: Int, pageSize: Int): Page<E> {
        val total = count(criteria)
        val items = fetch(criteria, page * pageSize, pageSize)
        return Page.of(items, page, pageSize, total)
    }

    /**
     * Busca pela chave. Implementada na interface de cada entidade, como um default que monta o
     * `XxxCriteria` com igualdade sobre a chave e delega a [fetch] — assim buscar por id é a mesma
     * consulta das outras, com o mesmo tratamento de projeção, segurança e transação.
     *
     * @param projection `null` projeta [newProjection]
     * @return a entidade, ou `null` se não houver linha com essa chave
     */
    suspend fun fetchById(id: K, projection: E? = null): E?

    fun newProjection(): E

    companion object {
        /**
         * Verifica se um campo deve entrar no UPDATE: a projeção indica o campo (não-nulo) e o valor mudou
         * em relação a [oldBean] (ou não há [oldBean]).
         */
        fun <E, V> changed(newBean: E, oldBean: E?, projection: E, getter: (E) -> V?): Boolean =
            getter(projection) != null && (oldBean == null || getter(newBean) != getter(oldBean))
    }
}
