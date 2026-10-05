package br.com.wdc.framework.jooq

import com.google.gson.stream.JsonReader
import org.jooq.Field
import org.jooq.Record1
import org.jooq.SelectJoinStep
import org.jooq.SelectQuery
import org.jooq.SortField
import org.jooq.Table

/** Completa a consulta de uma tabela: recebe a instância da tabela (com o alias da vez) e a consulta. */
typealias JsonWhereClause<T> = (table: T, query: SelectJoinStep<Record1<String>>) -> Unit

/**
 * Mapeamento bean ↔ tabela construído e pronto para uso — ver [JsonQueryBuilder].
 *
 * @param B tipo do bean
 * @param T tipo da tabela jOOQ
 */
interface JsonQuery<B : Any, T : Table<*>> {

    /** Cria um bean vazio. */
    fun newBean(): B

    /**
     * Cria um bean de projeção com todos os campos **escalares** marcados. [adapt] pode anular os
     * indesejados (campo `null` não entra no SELECT) ou acrescentar relações.
     */
    fun newProjectionBean(adapt: ((B) -> Unit)? = null): B

    /** Cria uma instância da tabela com o alias informado. */
    fun newTable(alias: String): T

    /** Monta o SELECT (sem executar). */
    fun select(prjBean: B?, whereClause: JsonWhereClause<T>): SelectQuery<Record1<String>>

    /** Monta o SELECT num contexto existente; [isAgg] agrega as linhas num array JSON (uso das relações). */
    fun select(ctx: QueryContext, prjBean: B?, whereClause: JsonWhereClause<T>, isAgg: Boolean): SelectQuery<Record1<String>>

    /** Se esta consulta sabe traduzir o `OrderBy` do seu critério. */
    fun hasOrdering(): Boolean

    /** Traduz o `OrderBy` do critério em `ORDER BY`, contra a tabela informada. */
    fun orderingOf(table: T, criteria: Any?): List<SortField<*>>

    /**
     * Monta o SELECT agregado de uma coleção filha, ordenado e/ou recortado.
     *
     * **A ordem é função da tabela**, e não uma lista pronta: a instância da tabela filha — com o alias único
     * daquela consulta — só existe aqui dentro.
     *
     * @param order  traduz o critério em `ORDER BY`; `null` deixa a ordem a cargo do banco
     * @param limit  máximo de linhas, ou `null`
     * @param offset linhas a pular, ou `null`
     * @throws UnsupportedOperationException se há ordem e o dialeto não sabe ordenar dentro da agregação
     * @throws IllegalStateException se há recorte e a tabela não tem chave primária simples
     */
    fun selectOrdered(
        ctx: QueryContext,
        prjBean: B?,
        whereClause: JsonWhereClause<T>,
        order: ((T) -> List<SortField<*>>)?,
        limit: Int?,
        offset: Int?,
    ): SelectQuery<Record1<String>>

    /** O campo de projeção JSON para a tabela. */
    fun projection(ctx: QueryContext, table: T, prjBean: B?): Field<String>

    /**
     * Se a projeção pede algum campo fora da lista informada.
     *
     * Serve ao atalho de chave estrangeira: quando a projeção da associação não pede nada além da chave, o
     * valor já está na coluna da linha corrente, e a subconsulta ao outro lado é dispensável.
     */
    fun projectsBeyond(prjBean: B?, fields: Collection<String>): Boolean

    /** Lê um bean de um texto JSON. */
    fun parseJson(json: String): B

    /** Lê um bean do leitor, posicionado no início do objeto. */
    fun parseJson(reader: JsonReader): B

    /** Executa e devolve um único bean, ou `null`. */
    fun fetchOne(prjBean: B?, whereClause: JsonWhereClause<T>): B?

    /** Executa e devolve a lista. */
    fun fetchToList(prjBean: B?, whereClause: JsonWhereClause<T>): List<B>
}
