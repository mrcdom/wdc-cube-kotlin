package br.com.wdc.framework.jooq

import com.google.gson.stream.JsonReader
import org.jooq.Field
import org.jooq.Record1
import org.jooq.SelectJoinStep
import org.jooq.SelectQuery
import org.jooq.SortField
import org.jooq.Table

/** Completes a table's query: receives the table instance (with the current alias) and the query. */
typealias JsonWhereClause<T> = (table: T, query: SelectJoinStep<Record1<String>>) -> Unit

/**
 * Bean ↔ table mapping, built and ready to use — see [JsonQueryBuilder].
 *
 * @param B bean type
 * @param T jOOQ table type
 */
interface JsonQuery<B : Any, T : Table<*>> {

    /** Creates an empty bean. */
    fun newBean(): B

    /**
     * Creates a projection bean with all **scalar** fields marked. [adapt] can null out the unwanted ones
     * (a `null` field stays out of the SELECT) or add relations.
     */
    fun newProjectionBean(adapt: ((B) -> Unit)? = null): B

    /** Creates a table instance with the given alias. */
    fun newTable(alias: String): T

    /** Builds the SELECT (without running it). */
    fun select(prjBean: B?, whereClause: JsonWhereClause<T>): SelectQuery<Record1<String>>

    /** Builds the SELECT in an existing context; [isAgg] aggregates the rows into a JSON array (used by relations). */
    fun select(ctx: QueryContext, prjBean: B?, whereClause: JsonWhereClause<T>, isAgg: Boolean): SelectQuery<Record1<String>>

    /** Whether this query knows how to translate its criterion's `OrderBy`. */
    fun hasOrdering(): Boolean

    /** Translates the criterion's `OrderBy` into `ORDER BY`, against the given table. */
    fun orderingOf(table: T, criteria: Any?): List<SortField<*>>

    /**
     * Builds the aggregated SELECT of a child collection, ordered and/or sliced.
     *
     * **The order is a function of the table**, not a ready list: the child table instance — with that query's
     * unique alias — only exists in here.
     *
     * @param order  translates the criterion into `ORDER BY`; `null` leaves the order to the database
     * @param limit  maximum rows, or `null`
     * @param offset rows to skip, or `null`
     * @throws UnsupportedOperationException if there is an order and the dialect cannot order inside the aggregation
     * @throws IllegalStateException if there is a slice and the table has no simple primary key
     */
    fun selectOrdered(
        ctx: QueryContext,
        prjBean: B?,
        whereClause: JsonWhereClause<T>,
        order: ((T) -> List<SortField<*>>)?,
        limit: Int?,
        offset: Int?,
    ): SelectQuery<Record1<String>>

    /** The JSON projection field for the table. */
    fun projection(ctx: QueryContext, table: T, prjBean: B?): Field<String>

    /**
     * Whether the projection asks for any field outside the given list.
     *
     * Serves the foreign key shortcut: when the association's projection asks for nothing beyond the key, the
     * value is already in the current row's column, and the subquery to the other side can be skipped.
     */
    fun projectsBeyond(prjBean: B?, fields: Collection<String>): Boolean

    /**
     * The mapping's fields — scalars and association key parts —, in declaration order. Resolves the relations
     * declared with `lazy` before answering.
     */
    fun fields(): List<JsonQueryField<B, T>>

    /**
     * Inserts [bean] and completes it with what the database filled in, in a single round trip.
     *
     * The `INSERT` takes the fields that are table columns (not the derived ones) and that [projection] marks;
     * without a projection, those with a value in the bean. A field left out gets the column default. A given
     * primary key always goes in; the identity column never does. Then every column that was not written, or was
     * written null, comes back to the bean through the same mapping as the fetch: the identity, the defaults,
     * what a trigger changed. What comes back null is not written to the bean (the mapping setters take no null).
     *
     * @param onConflictDoNothing discards the conflicting row (`ON CONFLICT DO NOTHING`) instead of failing. On H2
     *   jOOQ emulates it with a `MERGE` by the primary key: there, only a primary key conflict is discarded
     * @return `true` if the row went in; `false` if the conflict discarded it, and then the bean stays as it was
     */
    fun insert(bean: B, projection: B? = null, onConflictDoNothing: Boolean = false): Boolean

    /** Reads a bean from a JSON text. */
    fun parseJson(json: String): B

    /** Reads a bean from the reader, positioned at the start of the object. */
    fun parseJson(reader: JsonReader): B

    /** Runs and returns a single bean, or `null`. */
    fun fetchOne(prjBean: B?, whereClause: JsonWhereClause<T>): B?

    /** Runs and returns the list. */
    fun fetchToList(prjBean: B?, whereClause: JsonWhereClause<T>): List<B>
}
