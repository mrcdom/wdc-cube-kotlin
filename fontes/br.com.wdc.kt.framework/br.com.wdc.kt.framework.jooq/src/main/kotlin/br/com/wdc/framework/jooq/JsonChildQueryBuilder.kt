package br.com.wdc.framework.jooq

import org.jooq.Record1
import org.jooq.SelectConditionStep
import org.jooq.SelectJoinStep

/**
 * O que a cláusula de correlação de uma relação recebe: a tabela pai, a tabela filha e a consulta filha em
 * montagem.
 *
 * Quando a coleção de projeção carrega critério (`HasCriteria`), ele chega por [criteria], para que a
 * cláusula o aplique na consulta filha.
 *
 * **A cláusula pode ser chamada mais de uma vez na mesma consulta** (coleção com recorte: uma vez para a
 * tabela agregada, outra para a do recorte) — não guarde estado entre chamadas.
 *
 * @param S tipo da tabela pai
 * @param C tipo da tabela filha
 */
class JsonChildQueryBuilder<S, C> internal constructor(val ctx: QueryContext, val superTable: S) {

    private var child: C? = null
    private var query: SelectJoinStep<Record1<String>>? = null

    /** A tabela filha, com o alias desta chamada. */
    val childTable: C get() = child ?: error("childTable só existe durante a cláusula de correlação")

    /** Critério propagado por uma coleção `HasCriteria`, ou `null`. */
    var criteria: Any? = null
        internal set

    /** O critério, se for do tipo esperado; senão `null`. */
    inline fun <reified X> criteriaAs(): X? = criteria as? X

    fun dsl(): SelectJoinStep<Record1<String>> = query ?: error("dsl() só existe durante a cláusula de correlação")

    fun where(): SelectConditionStep<Record1<String>> = dsl().where()

    fun uniqueName(name: String): String = ctx.alias(name)

    internal fun bind(childTable: C, query: SelectJoinStep<Record1<String>>) {
        this.child = childTable
        this.query = query
    }
}
