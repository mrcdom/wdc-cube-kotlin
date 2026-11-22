package br.com.wdc.framework.jooq.dialect

import br.com.wdc.framework.jooq.JsonDialect
import br.com.wdc.framework.jooq.JsonFieldEntry
import br.com.wdc.framework.jooq.JsonFieldType
import org.jooq.DSLContext
import org.jooq.InsertReturningStep
import org.jooq.Table
import org.jooq.Field
import org.jooq.QueryPart
import org.jooq.SortField
import org.jooq.impl.DSL

/**
 * PostgreSQL JSON dialect.
 *
 * **Why not `json_build_object()`:** it takes two arguments per field, and PostgreSQL limits a function
 * call to 100 arguments (`FUNC_MAX_ARGS`, a compile-time constant) — 50 fields. The object is built by
 * concatenation, without that limit:
 * ```
 * '{' || array_to_string(array[
 *   '"id":' || coalesce(to_json(t.id), 'null'),
 *   '"image":' || coalesce(to_json(encode(t.image, 'base64')), 'null')
 * ], ',') || '}'
 * ```
 * `array[...]` is a constructor, not a function; `to_json()` serializes any type to valid JSON.
 * Keys with a null value are **emitted** as `"k":null` (H2 omits them; reading treats both cases alike).
 */
object PostgresJsonDialect : JsonDialect {

    override fun jsonObject(entries: List<JsonFieldEntry>): Field<String> {
        if (entries.isEmpty()) {
            return DSL.inline("{}")
        }
        val sql = StringBuilder("\n'{' || array_to_string(array[\n  ")
        val args = ArrayList<QueryPart>()
        entries.forEachIndexed { i, e ->
            if (i > 0) sql.append(",\n  ")
            sql.append("'\"").append(escapeKey(e.key)).append("\":' || ").append(valueExpr(e, args))
        }
        sql.append("\n], ',') || '}'")
        return DSL.field(sql.toString(), String::class.java, *args.toTypedArray())
    }

    override fun jsonArrayAgg(element: Field<String>): Field<String> =
        DSL.field("'[' || COALESCE(string_agg({0}, ','), '') || ']'", String::class.java, element)

    override fun jsonArrayAgg(element: Field<String>, order: List<SortField<*>>?): Field<String> {
        if (order.isNullOrEmpty()) {
            return jsonArrayAgg(element)
        }
        return DSL.field(
            "'[' || COALESCE(string_agg({0}, ',' ORDER BY {1}), '') || ']'",
            String::class.java, element, DSL.list(order),
        )
    }

    override fun supportsOrderedAggregation(): Boolean = true

    private fun valueExpr(e: JsonFieldEntry, args: MutableList<QueryPart>): String {
        val ph = "{${args.size}}"
        args.add(e.field)
        return when (e.type) {
            // encode() breaks the line every 76 characters (RFC 2045); reading uses the MIME decoder, which tolerates it
            JsonFieldType.BINARY -> "coalesce(to_json(encode($ph, 'base64')), 'null')"
            JsonFieldType.RAW_JSON -> "coalesce($ph, 'null')"
            else -> "coalesce(to_json($ph), 'null')"
        }
    }

    private fun escapeKey(key: String): String = key.replace("'", "''").replace("\"", "\\\"")

    // A single, atomic statement: WITH inserted AS (INSERT … RETURNING *) SELECT <row> FROM inserted
    override fun insertAndSelect(
        dsl: DSLContext,
        insert: InsertReturningStep<*>,
        inserted: Table<*>,
        alias: String,
        row: Field<String>,
        writtenKey: Map<String, Any?>,
    ): String? = dsl.with(alias).`as`(insert.returning()).select(row).from(DSL.table(DSL.name(alias))).fetchOne()?.value1()
}
