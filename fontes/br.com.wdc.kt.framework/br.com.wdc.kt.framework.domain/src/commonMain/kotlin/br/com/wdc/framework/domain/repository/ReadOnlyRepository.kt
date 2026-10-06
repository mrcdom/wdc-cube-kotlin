package br.com.wdc.framework.domain.repository

import br.com.wdc.framework.domain.pagination.Page

/**
 * Contrato de um repositório **de somente leitura**: consultar, contar e paginar.
 *
 * Serve a duas situações:
 *
 * - **dados que não se gravam por aqui** — uma visão de painel, um relatório, um agregado calculado no banco.
 *   Quem os expõe implementa só este contrato, sem ter de inventar o que fazer com um `insert`;
 * - **código que só lê** — um serviço que declara depender de `ReadOnlyRepository` diz, pelo tipo, que não
 *   altera nada, e aceita tanto um repositório de leitura quanto um completo.
 *
 * [Repository] estende este contrato com as operações de escrita.
 *
 * @param E tipo da entidade (ou da linha da visão)
 * @param C tipo do critério de pesquisa
 * @param K tipo da chave
 */
interface ReadOnlyRepository<E, C, K> {

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

    /** A projeção padrão: o que uma consulta traz quando o critério não diz. */
    fun newProjection(): E
}
