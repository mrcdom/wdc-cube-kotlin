package br.com.wdc.framework.jooq

import org.jooq.DSLContext

/**
 * Building context of **one** query: generates unique aliases (root and subqueries do not collide) and gives
 * access to the [DSLContext], which is injected — see [JsonQueryBuilder.setDSLContextSupplier].
 */
class QueryContext(private val jooq: DSLContext) {

    private var uniqueInt = 0

    fun alias(name: String): String = name + nextUniqueInt()

    fun nextUniqueInt(): Int = ++uniqueInt

    fun dsl(): DSLContext = jooq
}
