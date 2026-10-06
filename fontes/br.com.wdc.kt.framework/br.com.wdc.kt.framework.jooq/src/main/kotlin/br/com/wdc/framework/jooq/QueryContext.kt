package br.com.wdc.framework.jooq

import org.jooq.DSLContext

/**
 * Contexto de montagem de **uma** consulta: gera aliases únicos (raiz e subconsultas não colidem) e dá acesso
 * ao [DSLContext], que é injetado — ver [JsonQueryBuilder.setDSLContextSupplier].
 */
class QueryContext(private val jooq: DSLContext) {

    private var uniqueInt = 0

    fun alias(name: String): String = name + nextUniqueInt()

    fun nextUniqueInt(): Int = ++uniqueInt

    fun dsl(): DSLContext = jooq
}
