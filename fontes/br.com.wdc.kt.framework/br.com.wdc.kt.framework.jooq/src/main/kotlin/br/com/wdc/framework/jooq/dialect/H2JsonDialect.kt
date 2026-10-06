package br.com.wdc.framework.jooq.dialect

import br.com.wdc.framework.jooq.JsonDialect
import br.com.wdc.framework.jooq.JsonFieldEntry
import br.com.wdc.framework.jooq.JsonFieldType
import java.sql.Connection
import java.util.Base64
import org.jooq.Field
import org.jooq.QueryPart
import org.jooq.SortField
import org.jooq.impl.DSL

/**
 * Dialeto JSON do H2, com a sintaxe SQL:2016:
 * ```
 * CAST(JSON_OBJECT(
 *     KEY 'id' VALUE t.ID,
 *     KEY 'image' VALUE TO_BASE64(t.IMAGE),
 *     KEY 'user' VALUE (subselect) FORMAT JSON
 *     ABSENT ON NULL
 * ) AS VARCHAR)
 * ```
 * Chaves de valor nulo são **omitidas**. Para Base64, usa a função `TO_BASE64`, registrada em [initialize].
 */
object H2JsonDialect : JsonDialect {

    override fun initialize(connection: Connection) {
        connection.createStatement().use { stmt ->
            stmt.execute("""CREATE ALIAS IF NOT EXISTS TO_BASE64 FOR "${H2JsonDialect::class.java.name}.toBase64"""")
        }
    }

    override fun jsonObject(entries: List<JsonFieldEntry>): Field<String> {
        if (entries.isEmpty()) {
            return DSL.inline("{}")
        }
        val sql = StringBuilder("CAST(JSON_OBJECT(")
        val args = ArrayList<QueryPart>()
        entries.forEachIndexed { i, e ->
            if (i > 0) sql.append(", ")
            sql.append("KEY '").append(e.key.replace("'", "''")).append("' VALUE ").append(valueExpr(e, args))
        }
        sql.append(" ABSENT ON NULL) AS VARCHAR)")
        return DSL.field(sql.toString(), String::class.java, *args.toTypedArray())
    }

    override fun jsonArrayAgg(element: Field<String>): Field<String> =
        DSL.field("'[' || COALESCE(LISTAGG({0}, ','), '') || ']'", String::class.java, element)

    override fun jsonArrayAgg(element: Field<String>, order: List<SortField<*>>?): Field<String> {
        if (order.isNullOrEmpty()) {
            return jsonArrayAgg(element)
        }
        return DSL.field(
            "'[' || COALESCE(LISTAGG({0}, ',') WITHIN GROUP (ORDER BY {1}), '') || ']'",
            String::class.java, element, DSL.list(order),
        )
    }

    override fun supportsOrderedAggregation(): Boolean = true

    private fun valueExpr(e: JsonFieldEntry, args: MutableList<QueryPart>): String {
        val ph = "{${args.size}}"
        args.add(e.field)
        return when (e.type) {
            JsonFieldType.BINARY -> "TO_BASE64($ph)"
            JsonFieldType.RAW_JSON -> "$ph FORMAT JSON"
            else -> ph
        }
    }

    /** `byte[]` → Base64. Registrada no H2 como `TO_BASE64`. */
    @JvmStatic
    fun toBase64(value: ByteArray?): String? = value?.let { Base64.getEncoder().encodeToString(it) }
}
