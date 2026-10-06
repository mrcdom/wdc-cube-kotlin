package br.com.wdc.framework.jooq.dialect

import br.com.wdc.framework.jooq.JsonDialect
import br.com.wdc.framework.jooq.JsonFieldEntry
import br.com.wdc.framework.jooq.JsonFieldType
import org.jooq.Field
import org.jooq.QueryPart
import org.jooq.SortField
import org.jooq.impl.DSL

/**
 * Dialeto JSON do PostgreSQL.
 *
 * **Por que não `json_build_object()`:** ela recebe dois argumentos por campo, e o PostgreSQL limita uma
 * chamada de função a 100 argumentos (`FUNC_MAX_ARGS`, constante de compilação) — 50 campos. O objeto é
 * montado por concatenação, sem esse limite:
 * ```
 * '{' || array_to_string(array[
 *   '"id":' || coalesce(to_json(t.id), 'null'),
 *   '"image":' || coalesce(to_json(encode(t.image, 'base64')), 'null')
 * ], ',') || '}'
 * ```
 * `array[...]` é um construtor, não uma função; `to_json()` serializa qualquer tipo para JSON válido.
 * Chaves de valor nulo são **emitidas** como `"k":null` (o H2 as omite; a leitura trata os dois casos igual).
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
            // encode() quebra a linha a cada 76 caracteres (RFC 2045); a leitura usa o decoder MIME, que tolera
            JsonFieldType.BINARY -> "coalesce(to_json(encode($ph, 'base64')), 'null')"
            JsonFieldType.RAW_JSON -> "coalesce($ph, 'null')"
            else -> "coalesce(to_json($ph), 'null')"
        }
    }

    private fun escapeKey(key: String): String = key.replace("'", "''").replace("\"", "\\\"")
}
