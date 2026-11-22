package br.com.wdc.framework.jooq

import org.jooq.Record1
import org.jooq.SelectConditionStep
import org.jooq.SelectJoinStep

/**
 * What a relation's correlation clause receives: the parent table, the child table and the child query being
 * built.
 *
 * When the projection collection carries a criterion (`HasCriteria`), it arrives through [criteria], so the
 * clause can apply it to the child query.
 *
 * **The clause may be called more than once in the same query** (sliced collection: once for the aggregated
 * table, again for the slice's) — do not keep state between calls.
 *
 * @param S parent table type
 * @param C child table type
 */
class JsonChildQueryBuilder<S, C> internal constructor(val ctx: QueryContext, val superTable: S) {

    private var child: C? = null
    private var query: SelectJoinStep<Record1<String>>? = null

    /** The child table, with this call's alias. */
    val childTable: C get() = child ?: error("childTable only exists during the correlation clause")

    /** Criterion propagated by a `HasCriteria` collection, or `null`. */
    var criteria: Any? = null
        internal set

    /** The criterion, if of the expected type; otherwise `null`. */
    inline fun <reified X> criteriaAs(): X? = criteria as? X

    fun dsl(): SelectJoinStep<Record1<String>> = query ?: error("dsl() only exists during the correlation clause")

    fun where(): SelectConditionStep<Record1<String>> = dsl().where()

    fun uniqueName(name: String): String = ctx.alias(name)

    internal fun bind(childTable: C, query: SelectJoinStep<Record1<String>>) {
        this.child = childTable
        this.query = query
    }
}
