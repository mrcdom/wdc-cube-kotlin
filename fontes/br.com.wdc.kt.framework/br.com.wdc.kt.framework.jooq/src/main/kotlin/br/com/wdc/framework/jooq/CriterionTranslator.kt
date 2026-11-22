package br.com.wdc.framework.jooq

import br.com.wdc.framework.domain.criteria.Criterion
import br.com.wdc.framework.domain.criteria.Operator
import br.com.wdc.framework.domain.criteria.TextCriterion
import org.jooq.Condition
import org.jooq.Field
import org.jooq.Table
import org.jooq.TableField
import org.jooq.impl.DSL

/**
 * Translates a criterion field into a jOOQ condition — the **only** criterion → SQL translation.
 *
 * What stays per entity is only what it knows and the framework does not: which column matches each field and,
 * when the domain type differs from the column's, how to convert the value.
 *
 * **An unset field returns `null`** and stays out of the query; an entirely empty criterion results in
 * `noCondition()` — never a false condition, which would turn "no filter" into "no results".
 */
object CriterionTranslator {

    /** Condition of a field whose value goes to the database as is. */
    fun <V : Any> translate(column: Field<out V?>, criterion: Criterion<*, V>?): Condition? =
        translateWith(column, criterion, null)

    /**
     * Condition of a field whose value must be converted before reaching the column. The converter applies
     * equally to `eq`, `between` and `isIn`.
     *
     * @param A value type in the domain — `Instant`, `Double`
     * @param B column type — `LocalDateTime`, `BigDecimal`
     */
    fun <A : Any, B : Any> translate(column: Field<out B?>, criterion: Criterion<*, A>?, converter: (A) -> B): Condition? =
        translateWith(column, criterion, converter)

    /**
     * Condition of a field that applies to **several** columns: matches the row where any of them satisfies the
     * whole field (each column gets all of the field's predicates; between columns, `OR`). It is the search for a
     * term in several fields — name, company name, document — without the criterion needing one field per column.
     *
     * An unset field, or a list without columns, returns `null`, like [translate].
     */
    fun <V : Any> translateAny(columns: List<Field<out V?>>, criterion: Criterion<*, V>?): Condition? {
        val ofColumns = columns.mapNotNull { translate(it, criterion) }
        return when {
            ofColumns.isEmpty() -> null
            ofColumns.size == 1 -> ofColumns[0]
            else -> DSL.or(ofColumns)
        }
    }

    /**
     * Condition of a field over the given table, not over the one that declared the column — serves the
     * correlated subquery, where the table instance has its own alias.
     */
    fun <V : Any> translate(table: Table<*>, column: TableField<*, out V?>, criterion: Criterion<*, V>?): Condition? {
        if (criterion == null || !criterion.isSet()) return null
        return translateWith(column(table, column), criterion, null)
    }

    /** Like the previous one, for a field that needs conversion. */
    fun <A : Any, B : Any> translate(
        table: Table<*>,
        column: TableField<*, out B?>,
        criterion: Criterion<*, A>?,
        converter: (A) -> B,
    ): Condition? {
        if (criterion == null || !criterion.isSet()) return null
        return translateWith(column(table, column), criterion, converter)
    }

    /**
     * The column, resolved in [table].
     *
     * **Refuses when it does not exist, instead of ignoring the filter.** Returning `noCondition()` there would
     * hand back the whole set as if the filter had been applied.
     */
    fun <V> column(table: Table<*>, column: TableField<*, V>): Field<V> =
        table.field(column) ?: throw IllegalArgumentException("column ${column.name} does not exist in ${table.name}")

    /** Joins the remaining conditions; an empty list (or only `null`s) returns `noCondition()`. */
    fun and(conditions: List<Condition?>): Condition {
        val useful = conditions.filterNotNull()
        return if (useful.isEmpty()) DSL.noCondition() else DSL.and(useful)
    }

    private fun <A : Any> translateWith(column: Field<*>, criterion: Criterion<*, A>?, converter: ((A) -> Any)?): Condition? {
        if (criterion == null || !criterion.isSet()) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        val target = column as Field<Any?>
        val ofField = criterion.predicates.mapNotNull { condition(target, it, converter) }
        return when {
            ofField.isEmpty() -> null
            ofField.size == 1 -> ofField[0]
            // AND by default — it is what makes ge(start) with le(end) mean a range, and ne(1) with ne(2) exclude
            // both. It only becomes OR when the field was marked with or(), and the disjunction stays within the field.
            criterion.disjunctive -> DSL.or(ofField)
            else -> DSL.and(ofField)
        }
    }

    private fun <A : Any> condition(column: Field<Any?>, predicate: Criterion.Predicate<A>, converter: ((A) -> Any)?): Condition? {
        val op = predicate.operator
        if (op == Operator.IS_NULL) return column.isNull
        if (op == Operator.IS_NOT_NULL) return column.isNotNull

        val values: List<Any?> = predicate.values.map { v -> if (v == null || converter == null) v else converter(v) }
        // Operator and values disagree: refuse. Ignoring it would drop the predicate from the SQL with the field still
        // counting as a given filter, and a delete "with criterion" would wipe the table. From the wire, Criterion.restore already refuses.
        require(op.accepts(values.size)) { "criterion: $op does not accept ${values.size} value(s)" }
        return when (op) {
            Operator.EQ -> column.eq(values[0])
            Operator.NE -> column.ne(values[0])
            Operator.GT -> column.gt(values[0])
            Operator.GE -> column.ge(values[0])
            Operator.LT -> column.lt(values[0])
            Operator.LE -> column.le(values[0])
            // like/ilike are only offered on TextCriterion, which whoever declares the field picks by the column type
            // The escape is declared: it is what containing/startingWith use, and some databases have no default escape.
            Operator.LIKE -> column.like(values[0] as String, TextCriterion.ESCAPE)
            Operator.ILIKE -> column.likeIgnoreCase(values[0] as String, TextCriterion.ESCAPE)
            Operator.BETWEEN -> column.between(values[0], values[1])
            // empty set: nothing belongs. Explicit false condition, because jOOQ varies by dialect with `in()`
            Operator.IN -> if (values.isEmpty()) DSL.falseCondition() else column.`in`(values)
            Operator.IS_NULL, Operator.IS_NOT_NULL -> null
        }
    }
}
