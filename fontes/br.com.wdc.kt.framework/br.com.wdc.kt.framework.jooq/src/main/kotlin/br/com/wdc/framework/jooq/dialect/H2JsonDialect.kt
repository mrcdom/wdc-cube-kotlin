package br.com.wdc.framework.jooq.dialect

import br.com.wdc.framework.jooq.JsonDialect
import br.com.wdc.framework.jooq.JsonFieldEntry
import br.com.wdc.framework.jooq.JsonFieldType
import java.sql.Connection
import java.sql.Statement
import java.util.Base64
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.InsertReturningStep
import org.jooq.QueryPart
import org.jooq.SortField
import org.jooq.Table
import org.jooq.conf.ParamType
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

    /**
     * `INSERT` simples, com as chaves geradas pelo JDBC, e depois o `SELECT` da linha pela chave primária: duas idas
     * ao banco.
     *
     * Não é `SELECT … FROM FINAL TABLE (INSERT …)`, que seria uma só: no H2 2.4, quando essa instrução falha numa
     * restrição (chave estrangeira, por exemplo), a linha inserida fica gravada — em autocommit e também dentro de
     * uma transação que depois comita. O `INSERT` simples é atômico.
     */
    override fun insertAndSelect(
        dsl: DSLContext,
        insert: InsertReturningStep<*>,
        inserted: Table<*>,
        alias: String,
        row: Field<String>,
        writtenKey: Map<String, Any?>,
    ): String? {
        val sql = insert.getSQL(ParamType.INDEXED)
        val binds = insert.bindValues
        val generated = HashMap<String, Any?>()
        val count = dsl.connectionResult { connection ->
            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { statement ->
                binds.forEachIndexed { i, value -> statement.setObject(i + 1, value) }
                val count = statement.executeUpdate()
                if (count > 0) {
                    statement.generatedKeys.use { keys ->
                        if (keys.next()) {
                            for (i in 1..keys.metaData.columnCount) generated[keys.metaData.getColumnName(i).uppercase()] = keys.getObject(i)
                        }
                    }
                }
                count
            }
        }
        if (count == 0) return null
        val primaryKey = inserted.primaryKey?.fields
            ?: throw IllegalStateException("ler de volta a linha inserida exige chave primária, e ${inserted.name} não declara")
        val byKey = primaryKey.map { pk ->
            val value = if (pk.name in writtenKey) writtenKey[pk.name] else generated[pk.name.uppercase()]
            @Suppress("UNCHECKED_CAST")
            (inserted.field(pk.name) as Field<Any?>).eq(value)
        }
        return dsl.select(row).from(inserted).where(byKey).fetchOne()?.value1()
    }
}
