package br.com.wdc.framework.jooq

import org.jooq.Field
import org.jooq.Table
import org.jooq.TableField

/**
 * A mapping field, as [JsonQueryBuilder] knows it: the name, the column, how to read and write it on the bean. It is
 * what [JsonQuery.insert] uses to build the `INSERT` without the repository repeating the mapping, and what a repository
 * uses to build its own writes (an `UPDATE` by the columns the projection marks, for example): [projected] says whether
 * the field was requested, [mark] requests it on a projection bean and [toColumn] gives the value in the column type.
 *
 * Associations declared with a key (`addBeanField(…, key = …)`) appear once per key part, named
 * `association.part` (`author.id`) and with the foreign key column of **this** table. Collections are left out.
 *
 * @param B bean type
 * @param T jOOQ table type
 */
class JsonQueryField<B : Any, T : Table<*>> internal constructor(
    /** The JSON name; for an association key part, `association.part`. */
    val name: String,
    /** The column, or the expression, over the table instance. */
    val column: (T) -> Field<*>,
    /** The value on the bean, as the domain keeps it (for an association, the associated entity's key part). */
    val get: (B) -> Any?,
    /** Writes the value on the bean; `null` writes nothing. For a missing association, creates the associated entity with just the key. */
    val set: (B, Any?) -> Unit,
    /** Whether the projection bean asks for this field. */
    val projected: (B) -> Boolean,
    /** Marks the field on a projection bean (for an association, creates the associated entity with just the key part marked). */
    val mark: (B) -> Unit,
    /**
     * The domain value (what [get] returns, non-null) in the column type: `Instant` → `LocalDateTime` in UTC,
     * `Double` → `BigDecimal`, enum → name... For the exact type, `DSL.val(value, column).value` (`DataType.convert` is deprecated in jOOQ).
     */
    val toColumn: (Any) -> Any?,
) {
    /** `true` when [column] is a column of [table], not an expression over it (derived field). */
    fun isColumnOf(table: T): Boolean {
        val field = column(table)
        return field is TableField<*, *> && table.fields().any { it == field }
    }

    override fun toString(): String = "JsonQueryField($name)"
}
